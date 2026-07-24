package com.nkolay.ucmd.cacheloader.service.impl;

import com.nkolay.ucmd.cacheloader.model.dto.CacheFilter;
import com.nkolay.ucmd.cacheloader.model.dto.CacheRecord;
import com.nkolay.ucmd.cacheloader.model.entity.MerchantEntity;
import com.nkolay.ucmd.cacheloader.repository.MerchantRepository;
import com.nkolay.ucmd.cacheloader.service.TableAccessor;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class MerchantTableAccessor implements TableAccessor {

    private final MerchantRepository repository;

    public MerchantTableAccessor(MerchantRepository repository) {
        this.repository = repository;
    }

    @Override
    public String tableName() {
        return "merchants";
    }

    @Override
    public List<CacheRecord> fetch(CacheFilter filter) {
        return repository.findAll(build(filter)).stream()
                .map(m -> new CacheRecord(String.valueOf(m.getId()), m))
                .toList();
    }

    // Desteklenen filtreler: id, name(trade_name/legal_name %like%), status, tckn(tax_number %like%)
    private Specification<MerchantEntity> build(CacheFilter f) {
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
                        cb.like(cb.lower(root.get("tradeName")), like),
                        cb.like(cb.lower(root.get("legalName")), like)
                ));
            }

            String status = f.get("status");
            if (status != null) {
                ps.add(cb.equal(root.get("status"), status));
            }

            String tckn = f.get("tckn");
            if (tckn != null) {
                ps.add(cb.like(root.get("taxNumber"), "%" + tckn + "%"));
            }

            return cb.and(ps.toArray(new Predicate[0]));
        };
    }
}
