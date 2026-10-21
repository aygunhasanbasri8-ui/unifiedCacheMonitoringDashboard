package com.ucmd.model.entity;


import com.ucmd.model.dto.InstanceType;
import jakarta.persistence.*;

@Entity
@Table(name = "instances")
public class InstanceRef {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)

    private Long id;
    private String name;
    private String host;
    private int port;
    private boolean enabled;


    /** Hazelcast cluster adı. Redis instance'larında null. */
    private String clusterName;

    /** Virgülle ayrılmış member listesi. Boşsa host:port kullanılır. */
    @Column(length = 1000)
    private String members;

    @Enumerated(EnumType.STRING)
    @Column(name = "type" , nullable = false)
    private InstanceType type;


    /** jmx_exporter scrape adresi(leri), virgülle ayrılmış.
     *  Boşsa JVM metrikleri UNAVAILABLE döner. */
    @Column(length = 1000)
    private String metricsUrl;

    /** Redis ACL kullanıcı adı. Boşsa kimlik doğrulamasız bağlanılır. */
    private String username;

    /** Parolanın okunacağı environment değişkeninin ADI.
     *  Parola asla veritabanında saklanmaz. */
    private String passwordEnvVar;

    private boolean tlsEnabled;

    public InstanceRef() {}

    public InstanceRef(Long id, String name, InstanceType type, String host, int port, boolean enabled, boolean tlsEnabled) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.host = host;
        this.port = port;
        this.enabled = enabled;
        this.tlsEnabled = tlsEnabled;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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

    public String getClusterName() { return clusterName; }
    public void setClusterName(String clusterName) { this.clusterName = clusterName; }

    public String getMembers() { return members; }
    public void setMembers(String members) { this.members = members; }

    public String getMetricsUrl() { return metricsUrl; }
    public void setMetricsUrl(String metricsUrl) { this.metricsUrl = metricsUrl; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordEnvVar() { return passwordEnvVar; }
    public void setPasswordEnvVar(String passwordEnvVar) { this.passwordEnvVar = passwordEnvVar; }

    public boolean isTlsEnabled() {return tlsEnabled;}
    public void setTlsEnabled(boolean tlsEnabled) {this.tlsEnabled = tlsEnabled;}

    public String resolveMembers() {
        if (members != null && !members.isBlank()) return members;
        return host + ":" + port;
    }

    public String connectionKey() {
        return (clusterName == null ? "" : clusterName) + "|" + resolveMembers();
    }
}
