package com.beemer.coinservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "beemer.pinata")
public class PinataProperties {
	private String baseUrl;
	private String jwt;
}
