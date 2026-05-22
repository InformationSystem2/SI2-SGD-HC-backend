package com.sgd_hc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.sgd_hc.security.config.tenant.FilteredJpaRepositoryImpl;

@EnableScheduling
@SpringBootApplication
@EnableJpaRepositories(repositoryBaseClass = FilteredJpaRepositoryImpl.class)
public class SgdHcApplication {

	public static void main(String[] args) {
		SpringApplication.run(SgdHcApplication.class, args);
	}

}
