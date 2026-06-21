package com.beemer.coinservice.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "beemer.siwe")
public class SiweProperties {

	private String domain;
	private String uri;
	private String issuer;
	private String privateKey;
	private String publicKey;
}
