package com.beemer.coinservice.infrastructure.config;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.azure.spring.cloud.autoconfigure.implementation.context.properties.AzureGlobalProperties;

@Configuration
@EnableWebSecurity
@SuppressWarnings("unused")
public class SecurityConfiguration {

	@Bean
	JwtDecoder azureJwtDecoder(AzureGlobalProperties azureProperties,
			OAuth2ResourceServerProperties resourceServerProperties) {
		String clientId = azureProperties.getCredential().getClientId();
		String issuer = resourceServerProperties.getJwt().getIssuerUri();
		NimbusJwtDecoder decoder = JwtDecoders.fromIssuerLocation(issuer);
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer),
				token -> token.getAudience().contains("api://" + clientId)
						? OAuth2TokenValidatorResult.success()
						: OAuth2TokenValidatorResult
								.failure(new OAuth2Error("invalid_token", "Invalid audience", null))));
		return decoder;
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, @Qualifier("siweJwtDecoder") JwtDecoder siweJwtDecoder,
			@Qualifier("azureJwtDecoder") JwtDecoder azureJwtDecoder, SiweProperties siweProperties,
			OAuth2ResourceServerProperties resourceServerProperties) throws Exception {

		String azureIssuer = resourceServerProperties.getJwt().getIssuerUri();
		String siweIssuer = siweProperties.getIssuer();

		JwtGrantedAuthoritiesConverter rolesConverter = new JwtGrantedAuthoritiesConverter();
		rolesConverter.setAuthoritiesClaimName("roles");
		rolesConverter.setAuthorityPrefix("");
		JwtAuthenticationConverter azureAuthConverter = new JwtAuthenticationConverter();
		azureAuthConverter.setJwtGrantedAuthoritiesConverter(rolesConverter);

		JwtAuthenticationProvider azureProvider = new JwtAuthenticationProvider(azureJwtDecoder);
		azureProvider.setJwtAuthenticationConverter(azureAuthConverter);

		AuthenticationManager azureManager = new ProviderManager(azureProvider);
		AuthenticationManager siweManager = new ProviderManager(new JwtAuthenticationProvider(siweJwtDecoder));
		Map<String, AuthenticationManager> managers = Map.of(azureIssuer, azureManager, siweIssuer, siweManager);

		http.cors(cors -> cors.configurationSource(corsConfigurationSource(siweProperties)))
				.authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health/**").permitAll()
						.requestMatchers("/auth/challenge").permitAll().requestMatchers("/actuator/**")
						.hasAnyAuthority("actuator.read", "api.access").requestMatchers("/wallet/**")
						.hasAnyAuthority("api.access", "SCOPE_wallet.access").anyRequest().hasAuthority("api.access"))
				.oauth2ResourceServer(oauth2 -> oauth2
						.authenticationManagerResolver(new JwtIssuerAuthenticationManagerResolver(managers::get)));

		return http.build();
	}

	private CorsConfigurationSource corsConfigurationSource(SiweProperties siweProperties) {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(List.of(siweProperties.getUri()));
		config.setAllowedMethods(List.of("GET"));
		config.setAllowedHeaders(List.of("Content-Type"));
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/auth/challenge", config);
		return source;
	}
}
