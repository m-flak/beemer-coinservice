package com.beemer.coinservice.cron;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.beemer.coinservice.NoSecurityTest;
import com.beemer.coinservice.chaintask.epoch.CreateEpoch;

class CronJobRegistryTests extends NoSecurityTest {

	@MockitoBean
	CreateEpoch createEpoch;

	@Autowired
	CronJobRegistry cronJobRegistry;

	@Test
	void getAvailableCronJobs_isNotEmpty() {
		assertThat(cronJobRegistry.getAvailableCronJobs()).isNotEmpty();
	}

	@Test
	void getInstanceForName_returnsNonNullForRegisteredJob() {
		assertThat(cronJobRegistry.getInstanceForName("createEpoch")).isNotNull();
	}
}
