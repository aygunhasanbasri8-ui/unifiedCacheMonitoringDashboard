package com.nkolay.ucmd.service;

import com.nkolay.ucmd.model.dto.KeyInfo;
import com.nkolay.ucmd.model.dto.PageInfo;
import java.util.List;

public interface KeyAnalysisService {

    PageInfo<KeyInfo> scanKeys(Long instanceId, String prefix, String cursor, int count);
    List<KeyInfo> getTopNKeys(Long instanceId, int n);
    long getKeyTTL(Long instanceId, String key);


}
