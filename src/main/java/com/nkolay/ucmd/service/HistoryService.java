package com.nkolay.ucmd.service;

import com.nkolay.ucmd.model.dto.HistoryResponse;
import com.nkolay.ucmd.model.entity.MetricSnapshot;

import java.util.List;

public interface HistoryService {

    HistoryResponse getHistory(Long instanceId, Integer lastMinutes, Integer maxPoints);

    /** Örneklemesiz ham kayıtlar; lastMinutes boşsa tüm geçmiş. */
    List<MetricSnapshot> getSnapshots(Long instanceId, Integer lastMinutes);
}
