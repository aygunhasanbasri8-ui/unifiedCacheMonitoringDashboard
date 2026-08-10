package com.nkolay.ucmd.service;

import com.nkolay.ucmd.model.dto.HistoryResponse;

public interface HistoryService {

    HistoryResponse getHistory(Long instanceId, Integer lastMinutes, Integer maxPoints);
}
