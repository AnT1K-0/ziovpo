package com.example.shop;

import com.example.shop.signature.SignatureProperties;
import com.example.shop.service.BinaryExportProperties;
import com.example.shop.service.MinioStorageProperties;
import com.example.shop.service.SignatureFileProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@EnableConfigurationProperties({
		SignatureProperties.class,
		BinaryExportProperties.class,
		MinioStorageProperties.class,
		SignatureFileProperties.class
})
@SpringBootApplication
public class ShopApplication {
	public static void main(String[] args) {
		SpringApplication.run(ShopApplication.class, args);
	}
}
