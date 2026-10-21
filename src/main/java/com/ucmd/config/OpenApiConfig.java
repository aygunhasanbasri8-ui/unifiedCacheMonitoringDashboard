package com.ucmd.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ucmdOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("UCMD — Unified Cache Monitoring Dashboard API")
                        .version("1.0.0")
                        .description("""
                                Redis ve Hazelcast instance'ları için salt-okunur
                                monitoring API'si.

                                NOT: Okuma uçları POST metodunu kullanır (parametre
                                taşıma amaçlı mimari tercih — ADR-0007).
                                """))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }

    /** Monitoring API — salt okunur yüzey. Teslim edilen doküman budur. */
    @Bean
    public GroupedOpenApi monitoringApi() {
        return GroupedOpenApi.builder()
                .group("monitoring")
                .pathsToMatch("/v1/instances/**", "/v1/health/**", "/v1/auth/**")
                .build();
    }

    /** Admin API — yazma uçları. */
    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("admin")
                .pathsToMatch("/v1/admin/**", "/v1/audit/**")
                .build();
    }
}
