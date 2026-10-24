package com.ucmd.service;

import com.ucmd.model.dto.CapacityForecast;

public interface CapacityForecastService {

    CapacityForecast forecast(Long instanceId, Integer windowHours);

    /** Alarm değerlendirmesi için; son hesaplanan sonucu kısa süre tekrar kullanır. */
    Double cachedDaysToFull(Long instanceId);
}
