package com.ucmd.service.impl;

import com.ucmd.config.AlertConfig;
import com.ucmd.enums.AlertState;
import com.ucmd.model.dto.ActiveAlert;
import com.ucmd.model.entity.AlertEvent;
import com.ucmd.model.entity.MetricSnapshot;
import com.ucmd.repository.AlertEventRepository;
import com.ucmd.service.AlertService;
import com.ucmd.service.AuditService;
import com.ucmd.service.alert.AlertChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

@Service
public class AlertServiceImpl implements AlertService {

    private static final Logger logger = LoggerFactory.getLogger(AlertServiceImpl.class);
    private static final int MAX_HISTORY = 500;

    private final AlertConfig config;
    private final AlertEventRepository repository;
    private final AuditService auditService;
    private final List<AlertChannel> channels;
    private final ExecutorService executor;
    private final Clock clock;

    /** kural + instance başına durum; yeniden başlatmada sıfırlanır (alarm o zaman yeniden açılır). */
    private final Map<String, RuleState> states = new ConcurrentHashMap<>();
    /** Sayaç farkı gerektiren metrikler (eviction) için bir önceki snapshot. */
    private final Map<Long, MetricSnapshot> previous = new ConcurrentHashMap<>();

    @Autowired
    public AlertServiceImpl(AlertConfig config, AlertEventRepository repository, AuditService auditService,
                            List<AlertChannel> channels, ExecutorService virtualThreadExecutor) {
        this(config, repository, auditService, channels, virtualThreadExecutor, Clock.systemDefaultZone());
    }

    AlertServiceImpl(AlertConfig config, AlertEventRepository repository, AuditService auditService,
                     List<AlertChannel> channels, ExecutorService executor, Clock clock) {
        this.config = config;
        this.repository = repository;
        this.auditService = auditService;
        this.channels = channels;
        this.executor = executor;
        this.clock = clock;
    }

    @Override
    public void evaluate(List<MetricSnapshot> snapshots, Map<Long, String> instanceNames) {
        if (!config.isEnabled() || config.getRules().isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        for (MetricSnapshot snapshot : snapshots) {
            String name = instanceNames.getOrDefault(snapshot.getInstanceId(), "#" + snapshot.getInstanceId());
            MetricSnapshot before = previous.put(snapshot.getInstanceId(), snapshot);
            for (AlertConfig.Rule rule : config.getRules()) {
                if (rule.getMetric() == null || !rule.appliesTo(name)) {
                    continue;
                }
                Reading reading = AlertMetrics.read(rule, snapshot, before);
                if (reading != null) {
                    step(rule, snapshot.getInstanceId(), name, reading, now);
                }
            }
        }
    }

    private void step(AlertConfig.Rule rule, Long instanceId, String instanceName, Reading reading, LocalDateTime now) {
        String key = rule.getName() + "|" + instanceId;
        RuleState state = states.computeIfAbsent(key, k -> new RuleState());
        state.instanceName = instanceName;

        if (!reading.breached()) {
            state.pendingSince = null;
            if (state.firingSince != null) {
                state.firingSince = null;
                publish(rule, instanceId, instanceName, AlertState.RESOLVED, reading, now);
            }
            return;
        }

        if (state.pendingSince == null) {
            state.pendingSince = now;
        }
        state.lastValue = reading.value();
        if (state.firingSince == null) {
            if (Duration.between(state.pendingSince, now).toMinutes() >= rule.getForMinutes()) {
                state.firingSince = now;
                state.lastNotified = now;
                state.message = message(rule, instanceName, reading, AlertState.FIRING);
                publish(rule, instanceId, instanceName, AlertState.FIRING, reading, now);
            }
        } else if (Duration.between(state.lastNotified, now).toMinutes() >= config.getRepeatIntervalMinutes()) {
            // Tekrar hatırlatması yeni bir olay değil; geçmişe yazılmıyor, sadece bildiriliyor
            state.lastNotified = now;
            notifyChannels(new AlertEvent(rule.getName(), instanceId, instanceName, rule.getSeverity(),
                    AlertState.FIRING, reading.value(), rule.getThreshold(),
                    message(rule, instanceName, reading, AlertState.FIRING) + " (sürüyor)", now));
        }
    }

    private void publish(AlertConfig.Rule rule, Long instanceId, String instanceName, AlertState alertState,
                         Reading reading, LocalDateTime now) {
        AlertEvent event = new AlertEvent(rule.getName(), instanceId, instanceName, rule.getSeverity(),
                alertState, reading.value(), rule.getThreshold(),
                message(rule, instanceName, reading, alertState), now);
        repository.save(event);
        auditService.log("ALERT_" + alertState.name(), event.getMessage());
        notifyChannels(event);
    }

    private void notifyChannels(AlertEvent event) {
        for (AlertChannel channel : channels) {
            if (!channel.isConfigured()) {
                continue;
            }
            // Yavaş bir webhook toplama turunu bekletmesin
            executor.submit(() -> {
                try {
                    channel.send(event);
                } catch (Exception e) {
                    logger.warn("Alarm bildirimi gönderilemedi ({}): {}", channel.name(), e.getMessage());
                }
            });
        }
    }

    static String message(AlertConfig.Rule rule, String instanceName, Reading reading, AlertState state) {
        String what = switch (rule.getMetric()) {
            case INSTANCE_DOWN -> "erişilemiyor";
            default -> String.format(Locale.ROOT, "%s = %s (eşik %s %s)", rule.getMetric(),
                    format(reading.value()), rule.getOperator() == AlertConfig.Operator.GT ? ">" : "<",
                    format(rule.getThreshold()));
        };
        return state == AlertState.RESOLVED
                ? instanceName + ": " + rule.getName() + " düzeldi"
                : instanceName + ": " + rule.getName() + " - " + what;
    }

    private static String format(Double value) {
        if (value == null) {
            return "-";
        }
        return value == Math.rint(value) ? String.valueOf(value.longValue()) : String.format(Locale.ROOT, "%.2f", value);
    }

    @Override
    public List<ActiveAlert> getActiveAlerts() {
        return states.entrySet().stream()
                .filter(e -> e.getValue().firingSince != null)
                .map(e -> {
                    String[] parts = e.getKey().split("\\|", 2);
                    AlertConfig.Rule rule = findRule(parts[0]);
                    RuleState s = e.getValue();
                    Long instanceId = Long.valueOf(parts[1]);
                    return new ActiveAlert(parts[0], instanceId, s.instanceName, rule != null ? rule.getSeverity() : null,
                            s.lastValue, rule != null ? rule.getThreshold() : null, s.message, s.firingSince);
                })
                .sorted(Comparator.comparing(ActiveAlert::since))
                .toList();
    }

    private AlertConfig.Rule findRule(String name) {
        return config.getRules().stream().filter(r -> name.equals(r.getName())).findFirst().orElse(null);
    }

    @Override
    public List<AlertEvent> getHistory(Long instanceId, int limit) {
        PageRequest page = PageRequest.of(0, Math.max(1, Math.min(limit, MAX_HISTORY)));
        return instanceId == null
                ? repository.findAllByOrderByOccurredAtDesc(page)
                : repository.findByInstanceIdOrderByOccurredAtDesc(instanceId, page);
    }

    @Override
    public List<AlertConfig.Rule> getRules() {
        return config.getRules();
    }

    @Scheduled(cron = "0 45 3 * * *")
    public void purgeOldEvents() {
        long deleted = repository.deleteByOccurredAtBefore(LocalDateTime.now(clock).minusDays(config.getRetentionDays()));
        if (deleted > 0) {
            logger.info("{} eski alarm kaydı silindi", deleted);
        }
    }

    record Reading(Double value, boolean breached) {
    }

    private static final class RuleState {
        String instanceName;
        LocalDateTime pendingSince;
        LocalDateTime firingSince;
        LocalDateTime lastNotified;
        Double lastValue;
        String message;
    }
}
