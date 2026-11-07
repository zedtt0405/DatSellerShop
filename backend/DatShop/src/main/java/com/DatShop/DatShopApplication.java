package com.DatShop;

import com.DatShop.config.CorsProperties;
import com.DatShop.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class})
public class DatShopApplication {

	public static void main(String[] args) {
		SpringApplication.run(DatShopApplication.class, args);
	}

}
