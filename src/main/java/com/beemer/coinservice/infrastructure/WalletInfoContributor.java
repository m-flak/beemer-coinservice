package com.beemer.coinservice.infrastructure;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Credentials;

import java.util.Map;

@Component
public class WalletInfoContributor implements InfoContributor {

	private final Credentials credentials;

	public WalletInfoContributor(Credentials credentials) {
		this.credentials = credentials;
	}

	@Override
	public void contribute(Info.Builder builder) {
		builder.withDetail("wallet", Map.of("address", credentials.getAddress()));
	}
}
