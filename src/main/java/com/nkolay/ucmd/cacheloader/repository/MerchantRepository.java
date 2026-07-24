package com.nkolay.ucmd.cacheloader.repository;

import com.nkolay.ucmd.cacheloader.model.entity.MerchantEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MerchantRepository
        extends JpaRepository<MerchantEntity, Long>,
                JpaSpecificationExecutor<MerchantEntity> {
}
