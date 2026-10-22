package com.aiyi.game.dnfserver;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan({"com.aiyi"})
@EnableScheduling
public class DnfServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(DnfServerApplication.class, args);
	}

}
