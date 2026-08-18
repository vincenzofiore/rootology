package io.github.vincenzofiore.rootology;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class RootologyApplicationTests {

	@Test
	void contextLoads() {
	}

}
