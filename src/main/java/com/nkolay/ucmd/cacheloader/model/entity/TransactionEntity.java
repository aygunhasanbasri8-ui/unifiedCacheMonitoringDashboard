package com.nkolay.ucmd.cacheloader.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * transactions tablosu (merchant_db). Kolon adları sim/merchant_producer.py DDL'i ile birebir.
 */
@Entity
@Table(name = "transactions")
public class TransactionEntity {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "transaction_ref")
    private String transactionRef;

    @Column(name = "terminal_id")
    private Long terminalId;

    @Column(name = "merchant_id")
    private Long merchantId;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "commission_amount")
    private BigDecimal commissionAmount;

    @Column(name = "net_amount")
    private BigDecimal netAmount;

    @Column(name = "currency")
    private String currency;

    @Column(name = "transaction_type")
    private String transactionType;

    @Column(name = "installment_count")
    private Integer installmentCount;

    @Column(name = "card_type")
    private String cardType;

    @Column(name = "card_brand")
    private String cardBrand;

    @Column(name = "masked_card_no")
    private String maskedCardNo;

    @Column(name = "status")
    private String status;

    @Column(name = "response_code")
    private String responseCode;

    @Column(name = "transaction_date")
    private LocalDateTime transactionDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTransactionRef() { return transactionRef; }
    public void setTransactionRef(String transactionRef) { this.transactionRef = transactionRef; }
    public Long getTerminalId() { return terminalId; }
    public void setTerminalId(Long terminalId) { this.terminalId = terminalId; }
    public Long getMerchantId() { return merchantId; }
    public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public BigDecimal getCommissionAmount() { return commissionAmount; }
    public void setCommissionAmount(BigDecimal commissionAmount) { this.commissionAmount = commissionAmount; }
    public BigDecimal getNetAmount() { return netAmount; }
    public void setNetAmount(BigDecimal netAmount) { this.netAmount = netAmount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }
    public Integer getInstallmentCount() { return installmentCount; }
    public void setInstallmentCount(Integer installmentCount) { this.installmentCount = installmentCount; }
    public String getCardType() { return cardType; }
    public void setCardType(String cardType) { this.cardType = cardType; }
    public String getCardBrand() { return cardBrand; }
    public void setCardBrand(String cardBrand) { this.cardBrand = cardBrand; }
    public String getMaskedCardNo() { return maskedCardNo; }
    public void setMaskedCardNo(String maskedCardNo) { this.maskedCardNo = maskedCardNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getResponseCode() { return responseCode; }
    public void setResponseCode(String responseCode) { this.responseCode = responseCode; }
    public LocalDateTime getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDateTime transactionDate) { this.transactionDate = transactionDate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
