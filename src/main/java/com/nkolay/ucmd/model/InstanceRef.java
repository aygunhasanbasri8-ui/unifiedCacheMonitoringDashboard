package com.nkolay.ucmd.model;

public class InstanceRef {
    private Long id;
    private String name;
    private String type;  // REDIS | HAZELCAST
    private String host;
    private int port;
    private boolean enabled;

    public InstanceRef() {}

    public InstanceRef(Long id, String name, String type, String host, int port, boolean enabled) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.host = host;
        this.port = port;
        this.enabled = enabled;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
