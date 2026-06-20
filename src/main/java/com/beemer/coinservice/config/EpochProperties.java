package com.beemer.coinservice.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "beemer.epoch")
public class EpochProperties {

	private String cron;
	private List<RewardToken> rewardTokens = new ArrayList<>();

	@Data
	public static class RewardToken {
		private long chainId;
		private String rewardToken;
	}
}
