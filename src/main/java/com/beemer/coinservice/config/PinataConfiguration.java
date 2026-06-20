package com.beemer.coinservice.config;

import com.beemer.coinservice.client.PinataClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

@Configuration
@ImportHttpServices(group = "pinata", types = PinataClient.class)
public class PinataConfiguration {

	@Bean
	RestClientHttpServiceGroupConfigurer pinataGroupConfigurer(PinataProperties props) {
		return groups -> groups.filterByName("pinata").forEachClient((group, clientBuilder) -> {
			clientBuilder.baseUrl(props.getBaseUrl()).defaultHeader(HttpHeaders.AUTHORIZATION,
					"Bearer " + props.getJwt());
		});
	}
}
