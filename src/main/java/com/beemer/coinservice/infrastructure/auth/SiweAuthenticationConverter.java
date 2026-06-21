package com.beemer.coinservice.infrastructure.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

public class SiweAuthenticationConverter implements AuthenticationConverter {

	static final String PARAM_SIGNED_MESSAGE = "signed_message";
	static final String PARAM_SIGNATURE = "signature";

	@Override
	public Authentication convert(HttpServletRequest request) {
		String grantType = request.getParameter(OAuth2ParameterNames.GRANT_TYPE);
		if (!SiweAuthenticationToken.GRANT_TYPE.getValue().equals(grantType)) {
			return null;
		}

		Authentication clientPrincipal = SecurityContextHolder.getContext().getAuthentication();

		String signedMessage = request.getParameter(PARAM_SIGNED_MESSAGE);
		String signature = request.getParameter(PARAM_SIGNATURE);

		if (!StringUtils.hasText(signedMessage)) {
			throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST);
		}
		if (!StringUtils.hasText(signature)) {
			throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST);
		}

		Map<String, Object> additionalParameters = new HashMap<>();
		request.getParameterMap().forEach((key, values) -> {
			if (!OAuth2ParameterNames.GRANT_TYPE.equals(key) && !PARAM_SIGNED_MESSAGE.equals(key)
					&& !PARAM_SIGNATURE.equals(key) && values.length == 1) {
				additionalParameters.put(key, values[0]);
			}
		});

		return new SiweAuthenticationToken(signedMessage, signature, clientPrincipal, additionalParameters);
	}
}
