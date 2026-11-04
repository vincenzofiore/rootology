package io.github.vincenzofiore.rootology;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.time.ZoneId;

@SpringBootApplication
public class RootologyApplication {

	/**
	 * "Now" for the answers (the current month, what grows today, the weather), in Italy. Tests replace it
	 * with a fixed date, so the quality suite (#20) gives the same answers on any day.
	 */
	@Bean
	Clock clock() {
		return Clock.system(ZoneId.of("Europe/Rome"));
	}

	public static void main(String[] args) {
		SpringApplication.run(RootologyApplication.class, args);
	}

}
