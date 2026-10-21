package com.ucmd.service;

import com.ucmd.model.dto.HistoryResponse;
import com.ucmd.model.entity.MetricSnapshot;

import java.util.List;

public interface HistoryService {

    HistoryResponse getHistory(Long instanceId, Integer lastMinutes, Integer maxPoints);

    /** Örneklemesiz ham kayıtlar; lastMinutes boşsa tüm geçmiş. */
    List<MetricSnapshot> getSnapshots(Long instanceId, Integer lastMinutes);
}
