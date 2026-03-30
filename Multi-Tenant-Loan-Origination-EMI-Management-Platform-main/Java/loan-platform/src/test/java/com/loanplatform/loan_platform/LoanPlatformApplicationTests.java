package com.loanplatform.loan_platform;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"app.security.admin.username=test_admin",
		"app.security.admin.password=REDACTED_SEE_ENV"
})
class LoanPlatformApplicationTests {

	@Test
	void contextLoads() {
	}

}
