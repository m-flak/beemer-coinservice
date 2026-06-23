package com.beemer.coinservice.cron;

import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.beemer.coinservice.NoSecurityTest;
import com.beemer.coinservice.chaintask.epoch.CreateEpoch;
import com.beemer.coinservice.infrastructure.config.BlockchainProperties;

class CreateEpochCronJobTests extends NoSecurityTest {

	@MockitoBean
	CreateEpoch createEpoch;

	@Autowired
	CreateEpochCronJob createEpochCronJob;

	@Autowired
	BlockchainProperties blockchainProperties;

	@Test
	void run_executesCreateEpochForEachChain() {
		createEpochCronJob.run();

		verify(createEpoch, times(blockchainProperties.getChains().size())).execute(anyLong(), any());
	}
}
