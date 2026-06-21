package com.beemer.coinservice.infrastructure.auth;

import com.moonstoneid.siwe.SiweMessage;
import com.moonstoneid.siwe.error.SiweException;
import com.beemer.coinservice.infrastructure.config.BlockchainProperties;
import com.beemer.coinservice.infrastructure.config.SiweProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/auth")
public class SiweController {

	private final NonceStore nonceStore;
	private final SiweProperties siweProperties;
	private final Set<Long> supportedChainIds;

	public SiweController(NonceStore nonceStore, SiweProperties siweProperties,
			BlockchainProperties blockchainProperties) {
		this.nonceStore = nonceStore;
		this.siweProperties = siweProperties;
		this.supportedChainIds = blockchainProperties.getChains().stream().map(BlockchainProperties.Chain::getChainId)
				.collect(Collectors.toSet());
	}

	@GetMapping("/challenge")
	public Map<String, String> challenge(@RequestParam String address, @RequestParam long chainId)
			throws SiweException {
		if (!supportedChainIds.contains(chainId)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported chainId: " + chainId);
		}

		String nonce = UUID.randomUUID().toString().replace("-", "");
		OffsetDateTime now = OffsetDateTime.now();

		String message = new SiweMessage.Builder(siweProperties.getDomain(), address, siweProperties.getUri(), "1",
				(int) chainId, nonce, now.toString()).statement("Sign in to access the Beemer API")
				.expirationTime(now.plusMinutes(5).toString()).build().toMessage();

		nonceStore.store(nonce, message);

		return Map.of("nonce", nonce, "message", message);
	}
}
