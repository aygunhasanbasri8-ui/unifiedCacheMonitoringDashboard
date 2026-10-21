package com.ucmd.repository;


import com.ucmd.model.entity.InstanceRef;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InstanceRepository extends JpaRepository<InstanceRef, Long> {

    List<InstanceRef> findByType(String type);

    List<InstanceRef> findByEnabled(boolean enabled);

}
