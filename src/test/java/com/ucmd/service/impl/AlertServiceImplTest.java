package com.ucmd.service.impl;

import com.ucmd.config.AlertConfig;
import com.ucmd.enums.AlertMetric;
import com.ucmd.enums.AlertState;
import com.ucmd.model.entity.AlertEvent;
import com.ucmd.model.entity.MetricSnapshot;
import com.ucmd.repository.AlertEventRepository;
import com.ucmd.service.AuditService;
import com.ucmd.service.CapacityForecastService;
import com.ucmd.service.alert.AlertChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Alarm motoru durum makinesi")
class AlertServiceImplTest {

    private final MutableClock clock = new MutableClock();
    private final List<AlertEvent> saved = new ArrayList<>();
    private final List<AlertEvent> sent = new ArrayList<>();
    private AlertConfig config;
    private AlertServiceImpl service;

    @BeforeEach
    void setUp() {
        config = new AlertConfig();
        config.setRepeatIntervalMinutes(30);
        config.setRules(new ArrayList<>(List.of(
                rule("bellek", AlertMetric.MEMORY_PERCENT, AlertConfig.Operator.GT, 85, 5),
                rule("down", AlertMetric.INSTANCE_DOWN, AlertConfig.Operator.GT, 0, 0))));

        AlertEventRepository repository = mock(AlertEventRepository.class);
        when(repository.save(any())).thenAnswer(inv -> {
            saved.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        AlertChannel channel = new AlertChannel() {
            public String name() { return "test"; }
            public boolean isConfigured() { return true; }
            public void send(AlertEvent event) { sent.add(event); }
        };
        service = new AlertServiceImpl(config, repository, mock(AuditService.class), List.of(channel),
                new DirectExecutor(), mock(CapacityForecastService.class), clock);
    }

    @Test
    @DisplayName("eşik for-minutes boyunca aşılınca bir kez açılır, düşünce kapanır")
    void firesAfterSustainedBreachAndResolves() {
        for (int minute = 0; minute <= 4; minute++) {
            tick(memory(90));
        }
        assertThat(saved).isEmpty();

        tick(memory(91));   // 5. dakika
        tick(memory(92));
        assertThat(saved).extracting(AlertEvent::getState).containsExactly(AlertState.FIRING);
        assertThat(service.getActiveAlerts()).singleElement()
                .satisfies(a -> assertThat(a.instanceName()).isEqualTo("redis-1"));

        tick(memory(40));
        assertThat(saved).extracting(AlertEvent::getState).containsExactly(AlertState.FIRING, AlertState.RESOLVED);
        assertThat(service.getActiveAlerts()).isEmpty();
    }

    @Test
    @DisplayName("kısa bir sıçrama alarm üretmez")
    void shortSpikeIsIgnored() {
        tick(memory(95));
        tick(memory(95));
        tick(memory(50));
        for (int i = 0; i < 4; i++) {
            tick(memory(95));
        }
        assertThat(saved).isEmpty();
    }

    @Test
    @DisplayName("süren alarm hatırlatılır ama geçmişe yeni olay yazılmaz")
    void repeatsNotificationWithoutNewEvent() {
        for (int minute = 0; minute <= 40; minute++) {
            tick(memory(90));
        }
        assertThat(saved).hasSize(1);
        assertThat(sent).hasSize(2);
        assertThat(sent.get(1).getMessage()).endsWith("(sürüyor)");
    }

    @Test
    @DisplayName("erişilemeyen instance bellek alarmını yanlışlıkla kapatmaz")
    void downSnapshotDoesNotResolveOtherRules() {
        for (int minute = 0; minute <= 6; minute++) {
            tick(memory(90));
        }
        MetricSnapshot down = new MetricSnapshot();
        down.setInstanceId(1L);
        down.setStatus("DOWN");
        tick(down);

        assertThat(saved).extracting(AlertEvent::getRuleName, AlertEvent::getState)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("bellek", AlertState.FIRING),
                        org.assertj.core.groups.Tuple.tuple("down", AlertState.FIRING));
    }

    @Test
    @DisplayName("maxmemory tanımsızken bellek kuralı karar vermez")
    void unknownMaxMemoryIsSkipped() {
        MetricSnapshot snapshot = memory(0);
        snapshot.setMemoryMax(0);
        for (int minute = 0; minute <= 10; minute++) {
            tick(snapshot);
        }
        assertThat(saved).isEmpty();
    }

    private void tick(MetricSnapshot snapshot) {
        snapshot.setCollectedAt(LocalDateTime.now(clock));
        service.evaluate(List.of(snapshot), Map.of(1L, "redis-1"));
        clock.advance(Duration.ofMinutes(1));
    }

    private static MetricSnapshot memory(double percent) {
        MetricSnapshot s = new MetricSnapshot();
        s.setInstanceId(1L);
        s.setStatus("UP");
        s.setMemoryMax(1000);
        s.setMemoryUsed((long) (percent * 10));
        return s;
    }

    private static AlertConfig.Rule rule(String name, AlertMetric metric, AlertConfig.Operator op,
                                         double threshold, int forMinutes) {
        AlertConfig.Rule r = new AlertConfig.Rule();
        r.setName(name);
        r.setMetric(metric);
        r.setOperator(op);
        r.setThreshold(threshold);
        r.setForMinutes(forMinutes);
        return r;
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-24T10:00:00Z");

        void advance(Duration d) { now = now.plus(d); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private static final class DirectExecutor extends AbstractExecutorService {
        @Override public void execute(Runnable command) { command.run(); }
        @Override public void shutdown() { }
        @Override public List<Runnable> shutdownNow() { return List.of(); }
        @Override public boolean isShutdown() { return false; }
        @Override public boolean isTerminated() { return false; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
    }
}
