package com.beemer.coinservice.infrastructure;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.Map;

@HttpExchange
public interface PinataClient {

	@JsonInclude(JsonInclude.Include.NON_NULL)
	record PinRequest<T>(T pinataContent, PinOptions pinataOptions, PinMetadata pinataMetadata) {
	}

	record PinOptions(String groupId, int cidVersion) {
	}

	record PinMetadata(String name, Map<String, String> keyvalues) {
	}

	record PinResponse(@JsonProperty("IpfsHash") String ipfsHash, @JsonProperty("PinSize") int pinSize,
			@JsonProperty("Timestamp") String timestamp, boolean isDuplicate) {
	}

	@PostExchange("/pinning/pinJSONToIPFS")
	<T> PinResponse pinJSON(@RequestBody PinRequest<T> request);
}
