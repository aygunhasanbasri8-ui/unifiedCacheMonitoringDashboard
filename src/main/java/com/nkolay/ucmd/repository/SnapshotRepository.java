package com.nkolay.ucmd.repository;

import com.nkolay.ucmd.model.entity.MetricSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SnapshotRepository extends JpaRepository<MetricSnapshot, Long>{

    List<MetricSnapshot> findByInstanceId(Long instanceId);

    List<MetricSnapshot> findByInstanceIdAndCollectedAtBetween(
            Long instanceId,
            LocalDateTime start,
            LocalDateTime end
    );

    List<MetricSnapshot> findByInstanceIdOrderByCollectedAtAsc(Long instanceId);

    List<MetricSnapshot> findByInstanceIdAndCollectedAtAfterOrderByCollectedAtAsc(
            Long instanceId, LocalDateTime after);

}
