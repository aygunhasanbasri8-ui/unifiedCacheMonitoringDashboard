package com.ucmd.repository;

import com.ucmd.model.entity.AlertEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AlertEventRepository extends JpaRepository<AlertEvent, Long> {

    List<AlertEvent> findAllByOrderByOccurredAtDesc(Pageable pageable);

    List<AlertEvent> findByInstanceIdOrderByOccurredAtDesc(Long instanceId, Pageable pageable);

    @Modifying
    @Transactional
    long deleteByOccurredAtBefore(LocalDateTime cutoff);
}
