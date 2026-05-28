package com.diploma.mrt.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
@ConditionalOnProperty(name = "app.read-replica-enabled", havingValue = "true")
public class ReadReplicaDataSourceConfig {

    @Bean(name = "readDataSource")
    @ConditionalOnExpression("'${app.read-datasource.url:}' != ''")
    public DataSource readDataSource(
            @org.springframework.beans.factory.annotation.Value("${app.read-datasource.url}") String url,
            @org.springframework.beans.factory.annotation.Value("${app.read-datasource.username}") String username,
            @org.springframework.beans.factory.annotation.Value("${app.read-datasource.password}") String password
    ) {
        return DataSourceBuilder.create()
                .driverClassName("org.postgresql.Driver")
                .url(url)
                .username(username)
                .password(password)
                .build();
    }

    @Bean(name = "readJdbcTemplate")
    @ConditionalOnBean(name = "readDataSource")
    public JdbcTemplate readJdbcTemplate(@Qualifier("readDataSource") DataSource readDataSource) {
        return new JdbcTemplate(readDataSource);
    }
}
