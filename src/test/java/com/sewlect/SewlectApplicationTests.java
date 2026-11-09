package com.sewlect;

import com.sewlect.support.AbstractPostgresIntegrationTest;
import com.sewlect.support.WebSliceTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.ai.model.chat=none",
		"spring.ai.model.embedding=none",
		WebSliceTestConfig.JWT_SECRET_PROPERTY,
		"r2.endpoint=https://r2.test.invalid",
		"r2.access-key-id=test",
		"r2.secret-access-key=test",
		"r2.bucket=test",
		"fashn.api-key=test",
		"deepinfra.api-key=test",
		"auth.rate-limit.subject-hash-secret=test-rate-limit-secret-at-least-32-chars"
})
class SewlectApplicationTests extends AbstractPostgresIntegrationTest {

	@Test
	void contextLoads() {
	}
}