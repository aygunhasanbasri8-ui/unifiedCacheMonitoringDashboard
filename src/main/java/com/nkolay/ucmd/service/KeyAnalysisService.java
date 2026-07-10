package com.nkolay.ucmd.service;

import com.nkolay.ucmd.model.KeyInfo;
import com.nkolay.ucmd.model.PageInfo;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Service
public class KeyAnalysisService {

    /**
     * Gerçek implementasyonda Redis/Hazelcast'tan SCAN ile çekecek
     */
    public PageInfo<KeyInfo> scanKeys(Long instanceId, String prefix, String cursor, int count) {
        List<KeyInfo> keys = new ArrayList<>();

        // Mock data
        for (int i = 0; i < count && i < 5; i++) {
            String keyName = prefix + "key_" + i;
            KeyInfo keyInfo = new KeyInfo(
                    keyName,
                    "string",
                    (long)Math.random() * 10000,
                    -1  // No expiration
            );
            keys.add(keyInfo);
        }

        PageInfo<KeyInfo> pageInfo = new PageInfo<>(keys, "cursor_1", true, count);
        pageInfo.setTotalElements(keys.size());
        return pageInfo;
    }

    /**
     * MEMORY USAGE komutunu kullanacak
     */
    public List<KeyInfo> getTopNKeys(Long instanceId, int n) {
        List<KeyInfo> topKeys = new ArrayList<>();

        // Mock data - boyuta göre sıralı
        for (int i = 0; i < n && i < 10; i++) {
            KeyInfo keyInfo = new KeyInfo(
                    "large_key_" + i,
                    "string",
                    (long) (Math.random() * 1000000),  // Random size
                    -1
            );
            topKeys.add(keyInfo);
        }

        // Boyuta göre azalan sırayla sort et (büyük olanlar başta)
        topKeys.sort((k1, k2) -> Long.compare(k2.getSizeBytes(), k1.getSizeBytes()));

        return topKeys.stream().limit(n).toList();
    }


    public long getKeyTTL(Long instanceId, String key) {
        // Mock: rastgele TTL
        return (long) (Math.random() * 3600);  // 0-3600 saniye
    }
}
