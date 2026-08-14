package com.finsight.finsight_ai.ai.chat;

import com.finsight.finsight_ai.TestcontainersConfiguration;
import com.finsight.finsight_ai.ai.chat.adapters.out.tools.SpendByCategoryTool;
import com.finsight.finsight_ai.ai.chat.domain.ChatRequest;
import com.finsight.finsight_ai.ai.chat.ports.in.ChatUseCase;
import com.finsight.finsight_ai.ai.chat.support.TenantContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_VERTEX_TESTS", matches = "(?i)true")
@Tag("live-ai")
public class TenantContextRealDispatchTest {

    @Autowired
    private ChatUseCase chatUseCase;

    @SpyBean
    private SpendByCategoryTool spendByCategoryTool;

    @Test
    public void testTenantContextSurvivesRealVertexAiDispatch() throws InterruptedException {
        // 1. Setup isolated thread
        UUID testUserId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        ChatRequest request = new ChatRequest("What is my total spending for food in 2026-07?", null);
        
        AtomicReference<UUID> capturedUserId = new AtomicReference<>();
        AtomicReference<String> toolThreadName = new AtomicReference<>();
        AtomicReference<String> callerThreadName = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        // 2. Intercept tool execution
        doAnswer(invocation -> {
            toolThreadName.set(Thread.currentThread().getName());
            try {
                capturedUserId.set(TenantContext.get().orElse(null));
            } catch (Exception e) {
                // Ignore, will be caught by assertions
            }
            return (String) invocation.callRealMethod();
        }).when(spendByCategoryTool).execute(any());

        // 3. Execute in a separate thread to prove it works outside JUnit main thread
        Thread testRunner = new Thread(() -> {
            callerThreadName.set(Thread.currentThread().getName());
            TenantContext.set(testUserId);
            try {
                chatUseCase.processChat(testUserId, request);
            } finally {
                TenantContext.clear();
                latch.countDown();
            }
        }, "custom-http-worker-thread");

        testRunner.start();
        boolean completed = latch.await(60, TimeUnit.SECONDS);

        // 4. Assertions
        assertThat(completed).as("Test timed out waiting for Vertex AI").isTrue();
        
        assertThat(toolThreadName.get())
                .as("Tool must be executed")
                .isNotNull();
                
        assertThat(toolThreadName.get())
                .as("Tool must execute on the exact same thread as the caller (no reactor thread hops)")
                .isEqualTo(callerThreadName.get());
                
        assertThat(capturedUserId.get())
                .as("TenantContext must survive dispatch and match the user ID")
                .isEqualTo(testUserId);
    }
}
