package com.nkolay.ucmd.config;

import com.nkolay.ucmd.model.dto.InstanceType;
import com.nkolay.ucmd.model.entity.InstanceRef;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/** İlk açılışta instances tablosu boşsa oluşturulan varsayılan instance kayıtları.
 *  Adresler application.yml'de ortam değişkeniyle override edilebilir; host üzerinde
 *  localhost, Docker Compose'ta servis adları kullanılır. */
@Configuration
@ConfigurationProperties(prefix = "ucmd.seed")
public class SeedInstanceConfig {

    private List<SeedInstance> instances = new ArrayList<>();

    public List<SeedInstance> getInstances() { return instances; }
    public void setInstances(List<SeedInstance> instances) { this.instances = instances; }

    public static class SeedInstance {
        private String name;
        private InstanceType type;
        private String host;
        private int port;
        private boolean enabled = true;
        private boolean tlsEnabled = false;
        private String clusterName;
        private String members;
        private String metricsUrl;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public InstanceType getType() { return type; }
        public void setType(InstanceType type) { this.type = type; }

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }

        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public boolean isTlsEnabled() { return tlsEnabled; }
        public void setTlsEnabled(boolean tlsEnabled) { this.tlsEnabled = tlsEnabled; }

        public String getClusterName() { return clusterName; }
        public void setClusterName(String clusterName) { this.clusterName = clusterName; }

        public String getMembers() { return members; }
        public void setMembers(String members) { this.members = members; }

        public String getMetricsUrl() { return metricsUrl; }
        public void setMetricsUrl(String metricsUrl) { this.metricsUrl = metricsUrl; }

        public InstanceRef toInstanceRef() {
            InstanceRef ref = new InstanceRef(null, name, type, host, port, enabled, tlsEnabled);
            ref.setClusterName(clusterName);
            ref.setMembers(members);
            ref.setMetricsUrl(metricsUrl);
            return ref;
        }
    }
}
