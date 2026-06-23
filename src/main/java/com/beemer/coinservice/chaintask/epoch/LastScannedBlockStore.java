package com.beemer.coinservice.chaintask.epoch;

import java.math.BigInteger;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

@Component
public class LastScannedBlockStore {

	private final Cache<Long, BigInteger> cache = Caffeine.newBuilder().build();

	Optional<BigInteger> get(Long chainId) {
		return Optional.ofNullable(cache.getIfPresent(chainId));
	}

	void update(Long chainId, BigInteger block) {
		cache.put(chainId, block);
	}
}
