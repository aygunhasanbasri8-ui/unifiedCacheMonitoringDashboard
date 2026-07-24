package com.nkolay.ucmd.cacheloader.service.impl;

import com.nkolay.ucmd.cacheloader.model.dto.CacheFilter;
import com.nkolay.ucmd.cacheloader.model.dto.CacheRecord;
import com.nkolay.ucmd.cacheloader.model.entity.TerminalEntity;
import com.nkolay.ucmd.cacheloader.repository.TerminalRepository;
import com.nkolay.ucmd.cacheloader.service.TableAccessor;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TerminalTableAccessor implements TableAccessor {

    private final TerminalRepository repository;

    public TerminalTableAccessor(TerminalRepository repository) {
        this.repository = repository;
    }

    @Override
    public String tableName() {
        return "terminals";
    }

    @Override
    public List<CacheRecord> fetch(CacheFilter filter) {
        return repository.findAll(build(filter)).stream()
                .map(t -> new CacheRecord(String.valueOf(t.getId()), t))
                .toList();
    }

    // Desteklenen filtreler: id, name(terminal_no/serial_no %like%), status, merchantId
    private Specification<TerminalEntity> build(CacheFilter f) {
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
                        cb.like(cb.lower(root.get("terminalCode")), like),
                        cb.like(cb.lower(root.get("serialNumber")), like)
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
