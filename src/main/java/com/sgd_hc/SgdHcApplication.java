package com.sgd_hc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.sgd_hc.security.config.tenant.FilteredJpaRepositoryImpl;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@EnableScheduling
@SpringBootApplication
@EnableJpaRepositories(
        repositoryBaseClass = FilteredJpaRepositoryImpl.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.sgd_hc\\.audit\\.repository\\..*"
        )
)
public class SgdHcApplication {

	public static void main(String[] args) {
		SpringApplication.run(SgdHcApplication.class, args);
	}

}
