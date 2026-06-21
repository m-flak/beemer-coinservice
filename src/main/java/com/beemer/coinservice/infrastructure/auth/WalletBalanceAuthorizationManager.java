package com.beemer.coinservice.infrastructure.auth;

import com.beemer.coinservice.infrastructure.ContractLoader;
import com.beemer.coinservice.infrastructure.config.BlockchainProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

@Slf4j
@Component
public class WalletBalanceAuthorizationManager implements AuthorizationManager<MethodInvocation> {

	private static final String BEEMER_CONTRACT = "Beemer";
	private static final BigDecimal DECIMALS = BigDecimal.TEN.pow(18);

	private final Map<Long, Web3j> web3jInstances;
	private final BlockchainProperties blockchainProperties;
	private final ContractLoader contractLoader;
	private final Cache<String, BigInteger> balanceCache = Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(1))
			.build();

	public WalletBalanceAuthorizationManager(Map<Long, Web3j> web3jInstances, BlockchainProperties blockchainProperties,
			ContractLoader contractLoader) {
		this.web3jInstances = web3jInstances;
		this.blockchainProperties = blockchainProperties;
		this.contractLoader = contractLoader;
	}

	@Override
	public AuthorizationResult authorize(Supplier<? extends Authentication> authSupplier, MethodInvocation invocation) {
		WalletBalance annotation = findAnnotation(invocation);
		if (annotation == null)
			return new AuthorizationDecision(true);

		Authentication auth = authSupplier.get();
		if (!(auth instanceof JwtAuthenticationToken jwtAuth))
			return new AuthorizationDecision(false);

		String walletAddress = jwtAuth.getToken().getSubject();
		BigInteger minimumWei = BigDecimal.valueOf(annotation.minimum()).multiply(DECIMALS).toBigIntegerExact();

		// totalBalanceOf includes locked (staked) tokens — checks the holder's full
		// Beemer position across all configured chains
		for (BlockchainProperties.Chain chain : blockchainProperties.getChains()) {
			BlockchainProperties.Chain.Contract contract = chain.getContracts().get(BEEMER_CONTRACT);
			if (contract == null)
				continue;
			Web3j web3j = web3jInstances.get(chain.getChainId());
			if (web3j == null)
				continue;

			String cacheKey = walletAddress + ":" + chain.getChainId();
			BigInteger balance = balanceCache.get(cacheKey,
					_ -> fetchBalance(walletAddress, contract.getAddress(), web3j, chain.getChainId()));

			if (balance != null && balance.compareTo(minimumWei) >= 0) {
				return new AuthorizationDecision(true);
			}
		}
		return new AuthorizationDecision(false);
	}

	private BigInteger fetchBalance(String walletAddress, String contractAddress, Web3j web3j, long chainId) {
		try {
			return contractLoader.loadBeemer(contractAddress, web3j).totalBalanceOf(walletAddress).send();
		} catch (Exception e) {
			log.warn("Failed to check Beemer balance for {} on chain {}: {}", walletAddress, chainId, e.getMessage());
			return BigInteger.ZERO;
		}
	}

	private WalletBalance findAnnotation(MethodInvocation invocation) {
		WalletBalance annotation = AnnotationUtils.findAnnotation(invocation.getMethod(), WalletBalance.class);
		if (annotation == null) {
			annotation = AnnotationUtils.findAnnotation(invocation.getMethod().getDeclaringClass(),
					WalletBalance.class);
		}
		return annotation;
	}
}
