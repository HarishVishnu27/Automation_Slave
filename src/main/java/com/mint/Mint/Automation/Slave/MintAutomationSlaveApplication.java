package com.mint.Mint.Automation.Slave;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class MintAutomationSlaveApplication {

	public static void main(String[] args) {
		SpringApplication.run(MintAutomationSlaveApplication.class, args);
	}

}
