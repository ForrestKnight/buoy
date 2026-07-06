package io.github.forrestknight.buoy;

import org.springframework.boot.SpringApplication;

public class TestBuoyApplication {

	public static void main(String[] args) {
		SpringApplication.from(BuoyApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
