package com.finsight.finsight_ai;

import com.finsight.finsight_ai.ai.AIGateway;
import com.finsight.finsight_ai.ai.chat.ports.out.ChatModelPort;
import com.finsight.finsight_ai.ai.chat.ports.out.EmbeddingPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class FinsightAiApplicationTests {

	@MockBean
	private AIGateway aiGateway;

	@MockBean
	private ChatModelPort chatModelPort;

	@MockBean
	private EmbeddingPort embeddingPort;

	@Test
	void contextLoads() {
	}

}
