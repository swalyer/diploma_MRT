package com.diploma.mrt.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

@Configuration
@ConditionalOnProperty(name = "app.audit.enabled", havingValue = "true")
public class AuditDataSourceConfig {

    @Bean(name = "auditDataSource")
    public DataSource auditDataSource(
            @Value("${app.audit.datasource.url}") String url,
            @Value("${app.audit.datasource.username}") String username,
            @Value("${app.audit.datasource.password}") String password
    ) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return dataSource;
    }

    @Bean(name = "auditJdbcTemplate")
    public JdbcTemplate auditJdbcTemplate(DataSource auditDataSource) {
        return new JdbcTemplate(auditDataSource);
    }
}
