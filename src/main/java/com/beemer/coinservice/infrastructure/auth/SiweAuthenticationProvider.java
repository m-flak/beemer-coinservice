package com.beemer.coinservice.infrastructure.auth;

import com.moonstoneid.siwe.SiweMessage;
import com.moonstoneid.siwe.error.SiweException;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.util.Assert;

import java.util.Collections;

public class SiweAuthenticationProvider implements AuthenticationProvider {

	private static final SiweMessage.Parser MESSAGE_PARSER = new SiweMessage.Parser();

	private final NonceStore nonceStore;
	private final OAuth2AuthorizationService authorizationService;
	private final OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator;
	private final String domain;

	public SiweAuthenticationProvider(NonceStore nonceStore, OAuth2AuthorizationService authorizationService,
			OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator, String domain) {
		Assert.notNull(nonceStore, "nonceStore cannot be null");
		Assert.notNull(authorizationService, "authorizationService cannot be null");
		Assert.notNull(tokenGenerator, "tokenGenerator cannot be null");
		Assert.hasText(domain, "domain cannot be blank");
		this.nonceStore = nonceStore;
		this.authorizationService = authorizationService;
		this.tokenGenerator = tokenGenerator;
		this.domain = domain;
	}

	@Override
	public Authentication authenticate(Authentication authentication) throws AuthenticationException {
		SiweAuthenticationToken siweAuth = (SiweAuthenticationToken) authentication;

		OAuth2ClientAuthenticationToken clientPrincipal = getAuthenticatedClientElseThrowInvalidClient(siweAuth);
		RegisteredClient registeredClient = clientPrincipal.getRegisteredClient();
		if (registeredClient == null) {
			throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
		}

		if (!registeredClient.getAuthorizationGrantTypes().contains(SiweAuthenticationToken.GRANT_TYPE)) {
			throw new OAuth2AuthenticationException(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT);
		}

		SiweMessage siweMessage;
		try {
			siweMessage = MESSAGE_PARSER.parse(siweAuth.getSignedMessage());
		} catch (SiweException e) {
			throw new OAuth2AuthenticationException(
					new OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST, "Malformed SIWE message", null));
		}

		String nonce = siweMessage.getNonce();
		String storedMessage = nonceStore.consume(nonce);
		if (storedMessage == null) {
			throw new OAuth2AuthenticationException(
					new OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST, "Invalid or expired nonce", null));
		}

		if (!storedMessage.equals(siweAuth.getSignedMessage())) {
			throw new OAuth2AuthenticationException(
					new OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST, "Message mismatch", null));
		}

		try {
			siweMessage.verify(domain, nonce, siweAuth.getSignature());
		} catch (SiweException e) {
			throw new OAuth2AuthenticationException(
					new OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST, "Invalid signature", null));
		}

		String walletAddress = siweMessage.getAddress().toLowerCase();
		UsernamePasswordAuthenticationToken principal = new UsernamePasswordAuthenticationToken(walletAddress, null,
				Collections.emptyList());

		OAuth2TokenContext tokenContext = DefaultOAuth2TokenContext.builder().registeredClient(registeredClient)
				.principal(principal).authorizationServerContext(AuthorizationServerContextHolder.getContext())
				.authorizedScopes(registeredClient.getScopes()).tokenType(OAuth2TokenType.ACCESS_TOKEN)
				.authorizationGrantType(SiweAuthenticationToken.GRANT_TYPE).authorizationGrant(siweAuth).build();

		OAuth2Token generatedToken = tokenGenerator.generate(tokenContext);
		if (generatedToken == null) {
			throw new OAuth2AuthenticationException(
					new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR, "Token generation failed", null));
		}

		OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
				generatedToken.getTokenValue(), generatedToken.getIssuedAt(), generatedToken.getExpiresAt(),
				registeredClient.getScopes());

		OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(registeredClient)
				.principalName(walletAddress).authorizationGrantType(SiweAuthenticationToken.GRANT_TYPE)
				.accessToken(accessToken).build();

		authorizationService.save(authorization);

		return new OAuth2AccessTokenAuthenticationToken(registeredClient, clientPrincipal, accessToken);
	}

	@Override
	public boolean supports(Class<?> authentication) {
		return SiweAuthenticationToken.class.isAssignableFrom(authentication);
	}

	private static OAuth2ClientAuthenticationToken getAuthenticatedClientElseThrowInvalidClient(
			Authentication authentication) {
		if (authentication.getPrincipal() instanceof OAuth2ClientAuthenticationToken clientPrincipal
				&& clientPrincipal.isAuthenticated()) {
			return clientPrincipal;
		}
		throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
	}
}
