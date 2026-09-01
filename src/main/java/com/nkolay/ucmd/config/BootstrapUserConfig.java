package com.nkolay.ucmd.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** İlk açılışta oluşturulan tek yönetici hesabı.
 *  Kullanıcı adı ve parola yapılandırmadan (.env) gelir; kodda gömülü
 *  kimlik bilgisi yoktur. Diğer kullanıcılar bu hesapla UI üzerinden açılır. */
@Configuration
@ConfigurationProperties(prefix = "ucmd.bootstrap-user")
public class BootstrapUserConfig {

    private String username;
    private String password;
    private String role;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public boolean isConfigured() {
        return username != null && !username.isBlank()
                && password != null && !password.isBlank();
    }
}
