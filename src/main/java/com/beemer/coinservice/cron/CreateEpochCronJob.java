package com.beemer.coinservice.cron;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.beemer.coinservice.chaintask.epoch.CreateEpoch;
import com.beemer.coinservice.chaintask.exception.ChainTaskFailureException;
import com.beemer.coinservice.infrastructure.config.BlockchainProperties;
import com.beemer.coinservice.infrastructure.config.EpochProperties;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class CreateEpochCronJob {

	@Autowired
	private BlockchainProperties blockchainProperties;

	@Autowired
	private EpochProperties epochProperties;

	@Autowired
	private ObjectProvider<CreateEpoch> createEpoch;

	@Scheduled(cron = "${beemer.epoch.cron:-}")
	public void run() {
		for (var chain : blockchainProperties.getChains()) {
			log.info("Preparing to create a new epoch on {} {}", chain.getName(), chain.getChainId());

			try {
				createEpoch.getObject().execute(chain.getChainId(), epochProperties);
			} catch (ChainTaskFailureException ctfe) {
				// TODO: do something for observability with getFailureStage
			}

		}
	}
}
