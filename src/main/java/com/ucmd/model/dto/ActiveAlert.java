package com.ucmd.model.dto;

import java.time.LocalDateTime;

/** Şu an süren alarm. */
public record ActiveAlert(String ruleName, Long instanceId, String instanceName, String severity,
                          Double observedValue, Double threshold, String message, LocalDateTime since) {
}
