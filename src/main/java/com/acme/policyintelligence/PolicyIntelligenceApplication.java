package com.acme.policyintelligence;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PolicyIntelligenceApplication {

	public static void main(String[] args) {
		SpringApplication.run(PolicyIntelligenceApplication.class, args);
	}

}
