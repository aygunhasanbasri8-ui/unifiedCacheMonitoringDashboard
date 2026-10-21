package com.ucmd.service;

import com.ucmd.enums.MatchMode;
import com.ucmd.model.dto.*;


public interface KeyAnalysisService {

    PageInfo<KeyInfo> scanKeys(Long instanceId, String prefix, String cursor, int count,
                               MatchMode matchMode, String mapName);
    TopKeysResponse getTopNKeys(Long instanceId, int n, Integer scanLimit);
    KeyTtlResponse getKeyTTL(Long instanceId, String key, String mapName);


}
