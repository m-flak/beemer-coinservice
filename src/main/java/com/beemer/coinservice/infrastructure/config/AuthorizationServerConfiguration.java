package com.beemer.coinservice.infrastructure.config;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2AccessTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.beemer.coinservice.infrastructure.auth.NonceStore;
import com.beemer.coinservice.infrastructure.auth.SiweAuthenticationConverter;
import com.beemer.coinservice.infrastructure.auth.SiweAuthenticationProvider;
import com.beemer.coinservice.infrastructure.auth.SiweAuthenticationToken;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

@Configuration
@SuppressWarnings("unused")
public class AuthorizationServerConfiguration {

	@Bean
	@Order(1)
	SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http,
			OAuth2AuthorizationService authorizationService, OAuth2TokenGenerator<?> tokenGenerator,
			NonceStore nonceStore, SiweProperties siweProperties) throws Exception {

		OAuth2AuthorizationServerConfigurer authorizationServerConfigurer = new OAuth2AuthorizationServerConfigurer();

		http.securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
				.cors(cors -> cors.configurationSource(corsConfigurationSource(siweProperties)))
				.with(authorizationServerConfigurer,
						as -> as.tokenEndpoint(
								token -> token.accessTokenRequestConverter(new SiweAuthenticationConverter())
										.authenticationProvider(new SiweAuthenticationProvider(nonceStore,
												authorizationService, tokenGenerator, siweProperties.getDomain()))))
				.authorizeHttpRequests(auth -> auth.anyRequest().authenticated());

		return http.build();
	}

	@Bean
	OAuth2AuthorizationService authorizationService() {
		return new InMemoryOAuth2AuthorizationService();
	}

	@Bean
	OAuth2TokenGenerator<?> tokenGenerator(JWKSource<SecurityContext> jwkSource) {
		JwtGenerator jwtGenerator = new JwtGenerator(new NimbusJwtEncoder(jwkSource));
		return new DelegatingOAuth2TokenGenerator(jwtGenerator, new OAuth2AccessTokenGenerator());
	}

	@Bean
	RegisteredClientRepository registeredClientRepository() {
		RegisteredClient siweClient = RegisteredClient.withId(UUID.randomUUID().toString()).clientId("beemer-ui")
				.clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
				.authorizationGrantType(SiweAuthenticationToken.GRANT_TYPE).scope("wallet.access")
				.tokenSettings(TokenSettings.builder().accessTokenTimeToLive(Duration.ofHours(1)).build()).build();

		return new InMemoryRegisteredClientRepository(siweClient);
	}

	@Bean
	JWKSource<SecurityContext> jwkSource(SiweProperties siweProperties) throws Exception {
		if (siweProperties.getPrivateKey() == null || siweProperties.getPrivateKey().isBlank()) {
			throw new IllegalStateException("beemer.siwe.private-key is not configured");
		}
		if (siweProperties.getPublicKey() == null || siweProperties.getPublicKey().isBlank()) {
			throw new IllegalStateException("beemer.siwe.public-key is not configured");
		}

		RSAPrivateKey privateKey = RsaKeyConverters.pkcs8()
				.convert(new ByteArrayInputStream(siweProperties.getPrivateKey().getBytes(StandardCharsets.UTF_8)));
		RSAPublicKey publicKey = RsaKeyConverters.x509()
				.convert(new ByteArrayInputStream(siweProperties.getPublicKey().getBytes(StandardCharsets.UTF_8)));

		RSAKey rsaKey = new RSAKey.Builder(publicKey).privateKey(privateKey).keyIDFromThumbprint().build();

		return new ImmutableJWKSet<>(new JWKSet(rsaKey));
	}

	@Bean
	JwtDecoder siweJwtDecoder(JWKSource<SecurityContext> jwkSource) {
		return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
	}

	@Bean
	AuthorizationServerSettings authorizationServerSettings(SiweProperties siweProperties) {
		return AuthorizationServerSettings.builder().issuer(siweProperties.getIssuer()).build();
	}

	private CorsConfigurationSource corsConfigurationSource(SiweProperties siweProperties) {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(List.of(siweProperties.getUri()));
		config.setAllowedMethods(List.of("POST"));
		config.setAllowedHeaders(List.of("Content-Type", "Authorization"));
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/oauth2/token", config);
		return source;
	}
}
