package com.sunstar.provider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * @author xuxueli 2018-10-28 00:38:13
 */
@EnableScheduling
@SpringBootApplication
public class PlatformOneDemoApplication {

	@Value("${simkey:defaultvalue}")
	public String simkey;
	public static void main(String[] args) {
        SpringApplication.run(PlatformOneDemoApplication.class, args);
	}

	@Scheduled(fixedDelay = 100L)
	public void testApolloConfig(){
		System.out.println(simkey);
	}
}