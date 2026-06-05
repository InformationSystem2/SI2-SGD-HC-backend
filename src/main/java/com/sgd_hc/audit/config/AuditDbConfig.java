package com.sgd_hc.audit.config;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.Map;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages = "com.sgd_hc.audit.repository",
        entityManagerFactoryRef = "auditEntityManagerFactory",
        transactionManagerRef = "auditTransactionManager"
)
public class AuditDbConfig {

    @Value("${audit.datasource.url}")
    private String auditDbUrl;

    @Value("${audit.datasource.username}")
    private String auditDbUsername;

    @Value("${audit.datasource.password}")
    private String auditDbPassword;

    @Bean
    public DataSource auditDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(auditDbUrl);
        ds.setUsername(auditDbUsername);
        ds.setPassword(auditDbPassword);
        ds.setDriverClassName("org.postgresql.Driver");
        return ds;
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean auditEntityManagerFactory(
            @Qualifier("auditDataSource") DataSource auditDataSource) {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(auditDataSource);
        em.setPackagesToScan("com.sgd_hc.audit.entity");
        em.setPersistenceUnitName("audit");

        JpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        em.setJpaVendorAdapter(vendorAdapter);

        em.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none"));

        return em;
    }

    @Bean
    public PlatformTransactionManager auditTransactionManager(
            @Qualifier("auditEntityManagerFactory") EntityManagerFactory auditEntityManagerFactory) {
        return new JpaTransactionManager(auditEntityManagerFactory);
    }

    @Bean(initMethod = "migrate")
    public Flyway auditFlyway(@Qualifier("auditDataSource") DataSource auditDataSource) {
        return Flyway.configure()
                .dataSource(auditDataSource)
                .locations("classpath:db/migration/audit")
                .baselineOnMigrate(true)
                .load();
    }
}
