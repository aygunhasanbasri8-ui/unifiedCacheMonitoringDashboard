package com.ucmd.service;

import com.ucmd.config.AlertConfig;
import com.ucmd.model.dto.ActiveAlert;
import com.ucmd.model.entity.AlertEvent;
import com.ucmd.model.entity.MetricSnapshot;

import java.util.List;
import java.util.Map;

public interface AlertService {

    /**
     * Zamanlayıcının bu turda topladığı snapshot'ları kurallara karşı değerlendirir.
     *
     * @param instanceNames instance id -> ad (bildirim metni ve kural filtresi için)
     */
    void evaluate(List<MetricSnapshot> snapshots, Map<Long, String> instanceNames);

    List<ActiveAlert> getActiveAlerts();

    List<AlertEvent> getHistory(Long instanceId, int limit);

    List<AlertConfig.Rule> getRules();
}
