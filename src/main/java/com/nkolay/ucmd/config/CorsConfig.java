package com.nkolay.ucmd.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** İzin verilen origin listesi yapılandırmadan gelir (ucmd.cors.allowed-origins).
 *  Tarayıcı same-origin POST isteklerinde de Origin başlığı gönderdiği için,
 *  nginx arkasından servis edilen frontend'in origin'i de listede olmalıdır —
 *  aksi halde Spring isteği "Invalid CORS request" ile 403 döner. */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public CorsConfig(@Value("${ucmd.cors.allowed-origins}") String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET","POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
