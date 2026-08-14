package com.finsight.finsight_ai;

import org.springframework.boot.SpringApplication;

public class TestFinsightAiApplication {

	public static void main(String[] args) {
		SpringApplication.from(FinsightAiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
