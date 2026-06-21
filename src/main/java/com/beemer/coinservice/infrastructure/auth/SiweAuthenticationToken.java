package com.beemer.coinservice.infrastructure.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationGrantAuthenticationToken;

import java.util.Map;

public class SiweAuthenticationToken extends OAuth2AuthorizationGrantAuthenticationToken {

	public static final AuthorizationGrantType GRANT_TYPE = new AuthorizationGrantType(
			"urn:ietf:params:oauth:grant-type:siwe");

	private final String signedMessage;
	private final String signature;

	public SiweAuthenticationToken(String signedMessage, String signature, Authentication clientPrincipal,
			Map<String, Object> additionalParameters) {
		super(GRANT_TYPE, clientPrincipal, additionalParameters);
		this.signedMessage = signedMessage;
		this.signature = signature;
	}

	public String getSignedMessage() {
		return signedMessage;
	}

	public String getSignature() {
		return signature;
	}
}
