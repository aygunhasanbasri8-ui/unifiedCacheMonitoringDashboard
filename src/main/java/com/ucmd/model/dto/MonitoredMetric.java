package com.ucmd.model.dto;

import com.ucmd.enums.MetricStatus;

// MonitoringServiceImpl'in timeout/stale işaretlemesini tek yerde yapabilmesi için
public interface MonitoredMetric {

    void setOverallStatus(MetricStatus status);

    void setMeta(MetricMeta meta);
}
