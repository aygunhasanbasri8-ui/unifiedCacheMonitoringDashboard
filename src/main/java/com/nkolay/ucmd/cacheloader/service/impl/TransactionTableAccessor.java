package com.nkolay.ucmd.cacheloader.service.impl;

import com.nkolay.ucmd.cacheloader.model.dto.CacheFilter;
import com.nkolay.ucmd.cacheloader.model.dto.CacheRecord;
import com.nkolay.ucmd.cacheloader.model.entity.TransactionEntity;
import com.nkolay.ucmd.cacheloader.repository.TransactionRepository;
import com.nkolay.ucmd.cacheloader.service.TableAccessor;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TransactionTableAccessor implements TableAccessor {

    private final TransactionRepository repository;

    public TransactionTableAccessor(TransactionRepository repository) {
        this.repository = repository;
    }

    @Override
    public String tableName() {
        return "transactions";
    }

    @Override
    public List<CacheRecord> fetch(CacheFilter filter) {
        return repository.findAll(build(filter)).stream()
                .map(t -> new CacheRecord(String.valueOf(t.getId()), t))
                .toList();
    }

    // Desteklenen filtreler: id, name(tx_no/masked_pan %like%), status, merchantId
    private Specification<TransactionEntity> build(CacheFilter f) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();

            String id = f.get("id");
            if (id != null) {
                ps.add(cb.equal(root.get("id"), Long.valueOf(id)));
            }

            String name = f.get("name");
            if (name != null) {
                String like = "%" + name.toLowerCase() + "%";
                ps.add(cb.or(
                        cb.like(cb.lower(root.get("transactionRef")), like),
                        cb.like(cb.lower(root.get("maskedCardNo")), like)
                ));
            }

            String status = f.get("status");
            if (status != null) {
                ps.add(cb.equal(root.get("status"), status));
            }

            String merchantId = f.get("merchantId");
            if (merchantId != null) {
                ps.add(cb.equal(root.get("merchantId"), Long.valueOf(merchantId)));
            }

            return cb.and(ps.toArray(new Predicate[0]));
        };
    }
}
