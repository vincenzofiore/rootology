package app.rootology;

import org.springframework.boot.SpringApplication;

public class TestRootologyApplication {

	public static void main(String[] args) {
		SpringApplication.from(RootologyApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
