package io.github.forrestknight.buoy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BuoyApplication {

	public static void main(String[] args) {
		SpringApplication.run(BuoyApplication.class, args);
	}

}
