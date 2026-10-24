package com.ucmd.model.entity;

import com.ucmd.enums.AlertState;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Bir alarmın başlaması ya da bitmesi; geçmiş ekranı ve denetim için saklanır. */
@Entity
@Table(name = "alert_events", indexes = {
        @Index(name = "idx_alert_events_instance_time", columnList = "instanceId, occurredAt")
})
public class AlertEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String ruleName;

    private Long instanceId;
    private String instanceName;

    @Column(length = 16)
    private String severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AlertState state;

    /** Olay anındaki değer; INSTANCE_DOWN gibi değeri olmayan kurallarda null. */
    private Double observedValue;
    private Double threshold;

    @Column(length = 512)
    private String message;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    public AlertEvent() {
    }

    public AlertEvent(String ruleName, Long instanceId, String instanceName, String severity,
                      AlertState state, Double observedValue, Double threshold, String message,
                      LocalDateTime occurredAt) {
        this.ruleName = ruleName;
        this.instanceId = instanceId;
        this.instanceName = instanceName;
        this.severity = severity;
        this.state = state;
        this.observedValue = observedValue;
        this.threshold = threshold;
        this.message = message;
        this.occurredAt = occurredAt;
    }

    public Long getId() { return id; }
    public String getRuleName() { return ruleName; }
    public Long getInstanceId() { return instanceId; }
    public String getInstanceName() { return instanceName; }
    public String getSeverity() { return severity; }
    public AlertState getState() { return state; }
    public Double getObservedValue() { return observedValue; }
    public Double getThreshold() { return threshold; }
    public String getMessage() { return message; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
}
