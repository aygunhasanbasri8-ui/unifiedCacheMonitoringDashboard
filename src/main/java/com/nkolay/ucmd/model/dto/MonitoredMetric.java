package com.nkolay.ucmd.model.dto;

import com.nkolay.ucmd.enums.MetricStatus;

// MonitoringServiceImpl'in timeout/stale işaretlemesini tek yerde yapabilmesi için
public interface MonitoredMetric {

    void setOverallStatus(MetricStatus status);

    void setMeta(MetricMeta meta);
}
