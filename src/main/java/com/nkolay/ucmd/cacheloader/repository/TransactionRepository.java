package com.nkolay.ucmd.cacheloader.repository;

import com.nkolay.ucmd.cacheloader.model.entity.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TransactionRepository
        extends JpaRepository<TransactionEntity, Long>,
                JpaSpecificationExecutor<TransactionEntity> {
}
