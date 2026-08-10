package com.nkolay.ucmd.repository;

import com.nkolay.ucmd.model.entity.MetricSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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

    @Modifying
    @Transactional
    long deleteByCollectedAtBefore(LocalDateTime cutoff);

    Optional<MetricSnapshot> findFirstByInstanceIdOrderByCollectedAtDesc(Long instanceId);

    long countByInstanceId(Long instanceId);

}
