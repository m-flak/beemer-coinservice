package com.beemer.coinservice;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

@SpringBootTest
@SuppressWarnings("unused")
public abstract class NoSecurityTest {

	@MockitoBean
	@SuppressWarnings("unchecked")
	JWKSource<SecurityContext> jwkSource;

	@MockitoBean(name = "authorizationServerSecurityFilterChain")
	SecurityFilterChain authorizationServerSecurityFilterChain;

	@MockitoBean(name = "securityFilterChain")
	SecurityFilterChain securityFilterChain;

	@MockitoBean(name = "azureJwtDecoder")
	JwtDecoder azureJwtDecoder;

	@MockitoBean(name = "siweJwtDecoder")
	JwtDecoder siweJwtDecoder;
}
