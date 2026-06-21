package com.beemer.coinservice;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
		"beemer.siwe.issuer=http://localhost",
		"beemer.siwe.domain=localhost",
		"beemer.siwe.uri=http://localhost:4200"
})
class CoinserviceApplicationTests {

	@MockitoBean
	@SuppressWarnings("unchecked")
	JWKSource<SecurityContext> jwkSource;

	@MockitoBean(name = "securityFilterChain")
	SecurityFilterChain securityFilterChain;

	@MockitoBean(name = "azureJwtDecoder")
	JwtDecoder azureJwtDecoder;

	@Test
	void contextLoads() {
	}

}
