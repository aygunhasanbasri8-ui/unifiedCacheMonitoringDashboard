package com.nkolay.ucmd.cacheloader.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * merchants tablosu (merchant_db). Kolon adları sim/merchant_producer.py DDL'i ile birebir.
 */
@Entity
@Table(name = "merchants")
public class MerchantEntity {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "merchant_code")
    private String merchantCode;

    @Column(name = "tax_number")
    private String taxNumber;

    @Column(name = "legal_name")
    private String legalName;

    @Column(name = "trade_name")
    private String tradeName;

    @Column(name = "mcc_code")
    private String mccCode;

    @Column(name = "sector")
    private String sector;

    @Column(name = "city")
    private String city;

    @Column(name = "district")
    private String district;

    @Column(name = "address")
    private String address;

    @Column(name = "contact_person")
    private String contactPerson;

    @Column(name = "phone")
    private String phone;

    @Column(name = "email")
    private String email;

    @Column(name = "iban")
    private String iban;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "contract_date")
    private LocalDate contractDate;

    @Column(name = "contract_status")
    private String contractStatus;

    @Column(name = "commission_rate")
    private BigDecimal commissionRate;

    @Column(name = "daily_transaction_volume")
    private BigDecimal dailyTransactionVolume;

    @Column(name = "daily_transaction_count")
    private Integer dailyTransactionCount;

    @Column(name = "status")
    private String status;

    @Column(name = "risk_score")
    private Integer riskScore;

    @Column(name = "settlement_period")
    private String settlementPeriod;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getMerchantCode() { return merchantCode; }
    public void setMerchantCode(String merchantCode) { this.merchantCode = merchantCode; }
    public String getTaxNumber() { return taxNumber; }
    public void setTaxNumber(String taxNumber) { this.taxNumber = taxNumber; }
    public String getLegalName() { return legalName; }
    public void setLegalName(String legalName) { this.legalName = legalName; }
    public String getTradeName() { return tradeName; }
    public void setTradeName(String tradeName) { this.tradeName = tradeName; }
    public String getMccCode() { return mccCode; }
    public void setMccCode(String mccCode) { this.mccCode = mccCode; }
    public String getSector() { return sector; }
    public void setSector(String sector) { this.sector = sector; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getContactPerson() { return contactPerson; }
    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getIban() { return iban; }
    public void setIban(String iban) { this.iban = iban; }
    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }
    public LocalDate getContractDate() { return contractDate; }
    public void setContractDate(LocalDate contractDate) { this.contractDate = contractDate; }
    public String getContractStatus() { return contractStatus; }
    public void setContractStatus(String contractStatus) { this.contractStatus = contractStatus; }
    public BigDecimal getCommissionRate() { return commissionRate; }
    public void setCommissionRate(BigDecimal commissionRate) { this.commissionRate = commissionRate; }
    public BigDecimal getDailyTransactionVolume() { return dailyTransactionVolume; }
    public void setDailyTransactionVolume(BigDecimal dailyTransactionVolume) { this.dailyTransactionVolume = dailyTransactionVolume; }
    public Integer getDailyTransactionCount() { return dailyTransactionCount; }
    public void setDailyTransactionCount(Integer dailyTransactionCount) { this.dailyTransactionCount = dailyTransactionCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getRiskScore() { return riskScore; }
    public void setRiskScore(Integer riskScore) { this.riskScore = riskScore; }
    public String getSettlementPeriod() { return settlementPeriod; }
    public void setSettlementPeriod(String settlementPeriod) { this.settlementPeriod = settlementPeriod; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
