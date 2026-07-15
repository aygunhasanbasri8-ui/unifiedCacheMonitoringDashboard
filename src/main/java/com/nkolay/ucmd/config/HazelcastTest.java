/**
package com.nkolay.ucmd.config;

import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.ClientConnectionStrategyConfig;

public class HazelcastTest {
    public static void main(String[] args) {
        // İstemci yapılandırması başlatılıyor
        ClientConfig clientConfig = new ClientConfig();

        // Strateji konfigürasyonu çağrılıyor (Hata burada çözülmüş olmalı)
        ClientConnectionStrategyConfig strategyConfig = clientConfig.getConnectionStrategyConfig();
        strategyConfig.setAsyncStart(true);

        System.out.println("Bağlantı stratejisi başarıyla yapılandırıldı!");
    }
}

*/