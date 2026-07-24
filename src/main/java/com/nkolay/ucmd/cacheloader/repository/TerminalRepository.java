package com.nkolay.ucmd.cacheloader.repository;

import com.nkolay.ucmd.cacheloader.model.entity.TerminalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TerminalRepository
        extends JpaRepository<TerminalEntity, Long>,
                JpaSpecificationExecutor<TerminalEntity> {
}
