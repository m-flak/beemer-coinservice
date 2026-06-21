package com.beemer.coinservice.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import java.util.Map;
import java.util.stream.Collectors;

@Configuration
public class Web3jConfiguration {

	@Bean
	public Credentials web3jCredentials(CredentialsProperties props) {
		if (props.getPrivateKey() == null || props.getPrivateKey().isBlank()) {
			throw new IllegalStateException("beemer:credentials:privateKey is not configured");
		}
		return Credentials.create(props.getPrivateKey());
	}

	@Bean
	public Map<Long, Web3j> web3jInstances(BlockchainProperties blockchainProperties) {
		return blockchainProperties.getChains().stream().collect(Collectors.toMap(
				BlockchainProperties.Chain::getChainId, chain -> Web3j.build(new HttpService(chain.getRpcUrl()))));
	}
}
