package com.beemer.coinservice.cron;

import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/v1/trigger")
@AllArgsConstructor
public class TriggerController {

	private final CronJobRegistry cronJobRegistry;

	@GetMapping("/jobs")
	public Set<String> jobs() {
		return cronJobRegistry.getAvailableCronJobs();
	}

	@PostMapping("/{jobName}")
	public void trigger(@PathVariable String jobName) {
		CronJob job = cronJobRegistry.getInstanceForName(jobName);

		if (job == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No cron job registered with name: " + jobName);
		}

		job.run();
	}
}
