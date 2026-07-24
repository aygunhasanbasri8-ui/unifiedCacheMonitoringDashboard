package com.nkolay.ucmd.cacheloader.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * CacheLoader modülüne özel ikinci datasource (merchant_db).
 * Sadece cacheloader entity/repository'lerini kapsar; core ucmd_db'ye dokunmaz.
 * ddl-auto=none: gerçek tabloların şemasını asla değiştirmez (read-only kullanım).
 */
@Configuration
@EnableJpaRepositories(
        basePackages = "com.nkolay.ucmd.cacheloader.repository",
        entityManagerFactoryRef = "cacheLoaderEntityManagerFactory",
        transactionManagerRef = "cacheLoaderTransactionManager"
)
public class CacheLoaderDbConfig {

    @Bean
    @ConfigurationProperties("cacheloader.datasource")
    public DataSourceProperties cacheLoaderDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    public DataSource cacheLoaderDataSource(
            @Qualifier("cacheLoaderDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean(name = "cacheLoaderEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean cacheLoaderEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("cacheLoaderDataSource") DataSource dataSource) {

        Map<String, Object> props = new HashMap<>();
        props.put("hibernate.hbm2ddl.auto", "none");
        props.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");

        return builder
                .dataSource(dataSource)
                .packages("com.nkolay.ucmd.cacheloader.model.entity")
                .persistenceUnit("cacheloader")
                .properties(props)
                .build();
    }

    @Bean(name = "cacheLoaderTransactionManager")
    public PlatformTransactionManager cacheLoaderTransactionManager(
            @Qualifier("cacheLoaderEntityManagerFactory") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }
}
