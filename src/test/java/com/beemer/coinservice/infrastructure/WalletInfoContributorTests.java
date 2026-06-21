package com.beemer.coinservice.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.Info;
import org.web3j.crypto.Credentials;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WalletInfoContributorTests {

	private static final String PRIVATE_KEY = "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef";

	@Test
	void contribute_addsWalletAddress() {
		Credentials credentials = Credentials.create(PRIVATE_KEY);
		WalletInfoContributor contributor = new WalletInfoContributor(credentials);

		Info.Builder builder = new Info.Builder();
		contributor.contribute(builder);

		Info info = builder.build();
		assertThat(info.getDetails()).containsKey("wallet");
		assertThat(((Map<?, ?>) info.getDetails().get("wallet")).get("address")).isEqualTo(credentials.getAddress());
	}
}
