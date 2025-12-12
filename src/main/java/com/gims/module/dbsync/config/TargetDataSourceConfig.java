package com.gims.module.dbsync.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Target DB DataSource 설정
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages = "com.gims.module.dbsync.repository.target",
        entityManagerFactoryRef = "targetEntityManagerFactory",
        transactionManagerRef = "targetTransactionManager"
)
public class TargetDataSourceConfig {

    @Bean(name = "targetDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.target")
    public DataSource targetDataSource() {
        return DataSourceBuilder.create()
                .type(com.zaxxer.hikari.HikariDataSource.class)
                .build();
    }

    @Bean(name = "targetEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean targetEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("targetDataSource") DataSource dataSource) {

        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", "update");
        properties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        properties.put("hibernate.show_sql", true);
        properties.put("hibernate.format_sql", true);

        return builder
                .dataSource(dataSource)
                .packages("com.gims.module.dbsync.entity.target")
                .persistenceUnit("target")
                .properties(properties)
                .build();
    }

    @Bean(name = "targetTransactionManager")
    public PlatformTransactionManager targetTransactionManager(
            @Qualifier("targetEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
