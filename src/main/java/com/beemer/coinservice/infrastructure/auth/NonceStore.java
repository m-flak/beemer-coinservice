package com.beemer.coinservice.infrastructure.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class NonceStore {

	private final Cache<String, String> cache = Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(5)).build();

	/** Stores nonce → full EIP-4361 message text. */
	public void store(String nonce, String message) {
		cache.put(nonce, message);
	}

	/**
	 * Validates and consumes the nonce — single use. Returns the stored message or
	 * null if invalid/expired.
	 */
	public String consume(String nonce) {
		String message = cache.getIfPresent(nonce);
		if (message != null) {
			cache.invalidate(nonce);
		}
		return message;
	}
}
