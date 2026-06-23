package com.beemer.coinservice.cron;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;

import com.beemer.coinservice.chaintask.epoch.CreateEpoch;
import com.beemer.coinservice.infrastructure.config.BlockchainProperties;
import com.beemer.coinservice.infrastructure.config.EpochProperties;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@com.beemer.coinservice.cron.annotation.CronJob(name = "createEpoch")
public class CreateEpochCronJob extends CronJob {
	private final EpochProperties epochProperties;

	private final ObjectProvider<CreateEpoch> createEpoch;

	@Autowired
	public CreateEpochCronJob(BlockchainProperties blockchainProperties, EpochProperties epochProperties,
			ObjectProvider<CreateEpoch> createEpoch) {
		super(blockchainProperties.getChains());

		this.epochProperties = epochProperties;
		this.createEpoch = createEpoch;
	}

	@Override
	@Scheduled(cron = "${beemer.epoch.cron:-}")
	public void run() {
		super.run(createEpoch, epochProperties);
	}
}
