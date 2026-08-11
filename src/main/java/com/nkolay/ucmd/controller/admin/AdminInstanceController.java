package com.nkolay.ucmd.controller.admin;

import com.nkolay.ucmd.model.entity.InstanceRef;
import com.nkolay.ucmd.security.AuditContext;
import com.nkolay.ucmd.security.RequireRole;
import com.nkolay.ucmd.service.AuditService;
import com.nkolay.ucmd.service.MonitoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Rehber §7 — yazma uçları monitoring API yüzeyinden ayrılmıştır. */
@Tag(name = "Admin — Instances", description = "Instance kaydı oluşturma/güncelleme/silme (yalnızca ADMIN)")
@RestController
@RequestMapping("/v1/admin/instances")
@RequireRole("ADMIN")
public class AdminInstanceController {

    
    private final MonitoringService monitoringService;

    
    private final AuditService auditService;

    
    private final AuditContext auditContext;

    public AdminInstanceController(MonitoringService monitoringService, AuditService auditService, AuditContext auditContext) {
        this.monitoringService = monitoringService;
        this.auditService = auditService;
        this.auditContext = auditContext;
    }

    @Operation(summary = "Instance kaydı oluştur",
               description = "Yeni Redis/Hazelcast instance kaydı. Hazelcast için "
                       + "clusterName, members ve metricsUrl alanları doldurulmalıdır.")
    @ApiResponse(responseCode = "200", description = "Oluşturuldu")
    @ApiResponse(responseCode = "403", description = "ADMIN yetkisi gerekli")
    @PostMapping
    public ResponseEntity<InstanceRef> createInstance(@RequestBody InstanceRef instance,
                                                      HttpServletRequest httpRequest) {
        InstanceRef created = monitoringService.createInstance(instance);

        auditService.log("INSTANCE_CREATED",
                auditContext.currentUsername(httpRequest),
                "id=" + created.getId() + ", name=" + created.getName()
                        + ", type=" + created.getType(),
                "SUCCESS",
                auditContext.clientIp(httpRequest));

        return ResponseEntity.ok(created);
    }

    @Operation(summary = "Instance kaydını güncelle",
               description = "Kayıt güncellenir; ilgili metrik cache'i ve Hazelcast "
                       + "client'ı geçersiz kılınır.")
    @ApiResponse(responseCode = "200", description = "Güncellendi")
    @ApiResponse(responseCode = "403", description = "ADMIN yetkisi gerekli")
    @ApiResponse(responseCode = "404", description = "Instance bulunamadı")
    @PutMapping("/{id}")
    public ResponseEntity<InstanceRef> updateInstance(
            @PathVariable Long id,
            @RequestBody InstanceRef instance,
            HttpServletRequest httpRequest) {

        instance.setId(id);
        InstanceRef updated = monitoringService.updateInstance(instance);

        auditService.log("INSTANCE_UPDATED",
                auditContext.currentUsername(httpRequest),
                "id=" + id + ", name=" + updated.getName(),
                "SUCCESS",
                auditContext.clientIp(httpRequest));

        return ResponseEntity.ok(updated);
    }

    @Operation(summary = "Instance kaydını sil",
               description = "Kayıt silinir, açık bağlantılar kapatılır. "
                       + "İzlenen cache sistemine dokunulmaz.")
    @ApiResponse(responseCode = "204", description = "Silindi")
    @ApiResponse(responseCode = "403", description = "ADMIN yetkisi gerekli")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteInstance(@PathVariable Long id,
                                               HttpServletRequest httpRequest) {
        monitoringService.deleteInstance(id);

        auditService.log("INSTANCE_DELETED",
                auditContext.currentUsername(httpRequest),
                "id=" + id,
                "SUCCESS",
                auditContext.clientIp(httpRequest));

        return ResponseEntity.noContent().build();
    }
}
