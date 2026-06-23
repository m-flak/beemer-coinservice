package com.beemer.coinservice.cron;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;

import com.beemer.coinservice.chaintask.ChainTask;
import com.beemer.coinservice.chaintask.exception.ChainTaskFailureException;
import com.beemer.coinservice.infrastructure.config.BlockchainProperties.Chain;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor
public class CronJob {
	private final List<Chain> blockChains;

	protected void run(ObjectProvider<? extends ChainTask> taskProvider, Object taskParameters) {
		for (var chain : blockChains) {
			ChainTask task = taskProvider.getObject();
			log.info("Preparing to run {} on {} ({})...", task.getClass().getSimpleName(), chain.getName(),
					chain.getChainId());

			try {
				task.execute(chain.getChainId(), taskParameters);
			} catch (ChainTaskFailureException ctfe) {
				log.error("Execution of {} on {} ({}) failed at stage: {}.", task.getClass().getSimpleName(),
						chain.getName(), chain.getChainId(), ctfe.getFailureStage());
				log.error("Execution failed because: ", ctfe);
			}
		}
	}
}
