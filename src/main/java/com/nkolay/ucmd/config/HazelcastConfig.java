package com.nkolay.ucmd.config;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.ClientConnectionStrategyConfig;
import com.hazelcast.core.HazelcastInstance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration(proxyBeanMethods = false)
public class HazelcastConfig {

    @Value("${hazelcast.cluster-name:default}")
    private String clusterName;

    @Value("${hazelcast.members:localhost:5701}")
    private String members;

    @Bean
    public HazelcastInstance hazelcastClient() {
        ClientConfig config = new ClientConfig();

        //Cluster Adı
        config.setClusterName(clusterName);


        //member adresleri
        String[] memberlist = members.split(",");
        for (String member : memberlist) {
            config.getNetworkConfig().addAddress(member.trim());
        }

        //Bağlantı ayarları
        config.getConnectionStrategyConfig().getConnectionRetryConfig().setClusterConnectTimeoutMillis(5000);

        //Bağlanmasa uygulama çökmesin
        config.getConnectionStrategyConfig().setAsyncStart(true);

        return HazelcastClient.newHazelcastClient(config);
    }
}
