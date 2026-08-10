package com.nkolay.ucmd.service.impl;

import com.nkolay.ucmd.model.dto.HistoryPoint;
import com.nkolay.ucmd.model.dto.HistoryResponse;
import com.nkolay.ucmd.model.dto.MetricStatus;
import com.nkolay.ucmd.model.entity.MetricSnapshot;
import com.nkolay.ucmd.repository.SnapshotRepository;
import com.nkolay.ucmd.service.HistoryService;
import com.nkolay.ucmd.service.MonitoringService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
public class HistoryServiceImpl implements HistoryService {

    private final SnapshotRepository snapshotRepository;
    private final MonitoringService monitoringService;
    private final int defaultMaxPoints;
    private final int maxPointsLimit;

    public HistoryServiceImpl(SnapshotRepository snapshotRepository,
                              MonitoringService monitoringService,
                              @Value("${ucmd.snapshot.default-max-points:500}") int defaultMaxPoints,
                              @Value("${ucmd.snapshot.max-points-limit:2000}") int maxPointsLimit) {
        this.snapshotRepository = snapshotRepository;
        this.monitoringService = monitoringService;
        this.defaultMaxPoints = defaultMaxPoints > 0 ? defaultMaxPoints : 500;
        this.maxPointsLimit = maxPointsLimit > 0 ? maxPointsLimit : 2000;
    }

    @Override
    public HistoryResponse getHistory(Long instanceId, Integer lastMinutes, Integer maxPoints) {
        // Olmayan instance için 404 — SnapshotController bunu yapmıyordu
        monitoringService.getInstanceById(instanceId);

        int limit = (maxPoints != null && maxPoints > 0) ? maxPoints : defaultMaxPoints;
        limit = Math.min(limit, maxPointsLimit);

        List<MetricSnapshot> snapshots;
        if (lastMinutes != null && lastMinutes > 0) {
            LocalDateTime after = LocalDateTime.now().minusMinutes(lastMinutes);
            snapshots = snapshotRepository
                    .findByInstanceIdAndCollectedAtAfterOrderByCollectedAtAsc(instanceId, after);
        } else {
            snapshots = snapshotRepository.findByInstanceIdOrderByCollectedAtAsc(instanceId);
        }

        HistoryResponse response = new HistoryResponse();

        if (snapshots.isEmpty()) {
            response.setResolution("RAW");
            response.setStatus(MetricStatus.AVAILABLE);
            response.setPointCount(0);
            return response;
        }

        List<MetricSnapshot> selected;
        if (snapshots.size() <= limit) {
            selected = snapshots;
            response.setResolution("RAW");
        } else {
            // Basit stride sampling — ortalama/aggregate hesaplanmaz
            selected = new ArrayList<>(limit);
            int step = snapshots.size() / limit;
            for (int i = 0; i < snapshots.size() && selected.size() < limit; i += step) {
                selected.add(snapshots.get(i));
            }
            response.setResolution("DOWNSAMPLED");
        }

        List<HistoryPoint> points = new ArrayList<>(selected.size());
        for (MetricSnapshot s : selected) {
            points.add(toPoint(s));
        }

        response.setPoints(points);
        response.setPointCount(points.size());
        response.setFrom(toInstant(snapshots.get(0).getCollectedAt()));
        response.setTo(toInstant(snapshots.get(snapshots.size() - 1).getCollectedAt()));
        response.setStatus(MetricStatus.AVAILABLE);
        return response;
    }

    private HistoryPoint toPoint(MetricSnapshot s) {
        return new HistoryPoint(
                toInstant(s.getCollectedAt()),
                s.getStatus(),
                s.getCpuPercent(),
                s.getMemoryUsed(),
                s.getKeyCount(),
                s.getClientCount(),
                s.getHitRatio(),
                s.getOpsPerSec(),
                s.getEvictedKeys());
    }

    private Instant toInstant(LocalDateTime value) {
        return (value != null) ? value.atZone(ZoneId.systemDefault()).toInstant() : null;
    }
}
