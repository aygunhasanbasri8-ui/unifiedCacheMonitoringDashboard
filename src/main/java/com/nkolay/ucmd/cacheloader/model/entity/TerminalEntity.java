package com.nkolay.ucmd.cacheloader.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * terminals tablosu (merchant_db). Kolon adları sim/merchant_producer.py DDL'i ile birebir.
 */
@Entity
@Table(name = "terminals")
public class TerminalEntity {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "terminal_code")
    private String terminalCode;

    @Column(name = "merchant_id")
    private Long merchantId;

    @Column(name = "serial_number")
    private String serialNumber;

    @Column(name = "model")
    private String model;

    @Column(name = "terminal_type")
    private String terminalType;

    @Column(name = "installation_date")
    private LocalDate installationDate;

    @Column(name = "status")
    private String status;

    @Column(name = "daily_activity_status")
    private String dailyActivityStatus;

    @Column(name = "last_transaction_at")
    private LocalDateTime lastTransactionAt;

    @Column(name = "daily_transaction_count")
    private Integer dailyTransactionCount;

    @Column(name = "software_version")
    private String softwareVersion;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTerminalCode() { return terminalCode; }
    public void setTerminalCode(String terminalCode) { this.terminalCode = terminalCode; }
    public Long getMerchantId() { return merchantId; }
    public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getTerminalType() { return terminalType; }
    public void setTerminalType(String terminalType) { this.terminalType = terminalType; }
    public LocalDate getInstallationDate() { return installationDate; }
    public void setInstallationDate(LocalDate installationDate) { this.installationDate = installationDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDailyActivityStatus() { return dailyActivityStatus; }
    public void setDailyActivityStatus(String dailyActivityStatus) { this.dailyActivityStatus = dailyActivityStatus; }
    public LocalDateTime getLastTransactionAt() { return lastTransactionAt; }
    public void setLastTransactionAt(LocalDateTime lastTransactionAt) { this.lastTransactionAt = lastTransactionAt; }
    public Integer getDailyTransactionCount() { return dailyTransactionCount; }
    public void setDailyTransactionCount(Integer dailyTransactionCount) { this.dailyTransactionCount = dailyTransactionCount; }
    public String getSoftwareVersion() { return softwareVersion; }
    public void setSoftwareVersion(String softwareVersion) { this.softwareVersion = softwareVersion; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
