package com.finsight.finsight_ai.ai.chat.evaluation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RealProviderGateTest {

    @AfterEach
    void tearDown() {
        System.clearProperty("RUN_PHASE5_REAL_EVAL");
    }

    @Test
    @DisplayName("Test A: RUN_PHASE5_REAL_EVAL=false allows mock provider in CI test mode")
    void testA_RealModeOffAllowsMockProvider() {
        System.setProperty("RUN_PHASE5_REAL_EVAL", "false");

        EvaluationProviderProvenance mockProv = new EvaluationProviderProvenance(
                false, "MockProvider", "mock-model", "mock-project", "mock-location", "com.finsight.MockChatModel", "SIMULATED"
        );

        assertThatCode(() -> RealProviderGate.verifyGate(mockProv))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Test B: RUN_PHASE5_REAL_EVAL=true hard fails if mock provider active")
    void testB_RealModeOnRejectsMockProvider() {
        System.setProperty("RUN_PHASE5_REAL_EVAL", "true");

        EvaluationProviderProvenance mockProv = new EvaluationProviderProvenance(
                true, "MockProvider", "mock-model", "finsight-ai-dev", "us-central1", "com.finsight.MockChatModel", "SIMULATED"
        );

        assertThatThrownBy(() -> RealProviderGate.verifyGate(mockProv))
                .isInstanceOf(RealProviderNotActiveException.class)
                .hasMessageContaining("REAL_PROVIDER_NOT_ACTIVE: ChatModelPort implementation is mock/stub");
    }

    @Test
    @DisplayName("Test C: RUN_PHASE5_REAL_EVAL=true hard fails if model ID is missing")
    void testC_RealModeOnRejectsMissingModelId() {
        System.setProperty("RUN_PHASE5_REAL_EVAL", "true");

        EvaluationProviderProvenance missingModelProv = new EvaluationProviderProvenance(
                true, "Vertex AI", "", "finsight-ai-dev", "us-central1", "com.finsight.VertexChatAdapter", "CLOUD_REAL_VERTEX"
        );

        assertThatThrownBy(() -> RealProviderGate.verifyGate(missingModelProv))
                .isInstanceOf(RealProviderNotActiveException.class)
                .hasMessageContaining("REAL_PROVIDER_NOT_ACTIVE: Model ID is blank");
    }

    @Test
    @DisplayName("Test D: RUN_PHASE5_REAL_EVAL=true hard fails if project or location is missing")
    void testD_RealModeOnRejectsMissingProjectOrLocation() {
        System.setProperty("RUN_PHASE5_REAL_EVAL", "true");

        EvaluationProviderProvenance missingProjectProv = new EvaluationProviderProvenance(
                true, "Vertex AI", "gemini-2.5-flash-lite", "", "us-central1", "com.finsight.VertexChatAdapter", "CLOUD_REAL_VERTEX"
        );

        assertThatThrownBy(() -> RealProviderGate.verifyGate(missingProjectProv))
                .isInstanceOf(RealProviderNotActiveException.class)
                .hasMessageContaining("REAL_PROVIDER_NOT_ACTIVE: Project ID is blank");

        EvaluationProviderProvenance missingLocationProv = new EvaluationProviderProvenance(
                true, "Vertex AI", "gemini-2.5-flash-lite", "finsight-ai-dev", "", "com.finsight.VertexChatAdapter", "CLOUD_REAL_VERTEX"
        );

        assertThatThrownBy(() -> RealProviderGate.verifyGate(missingLocationProv))
                .isInstanceOf(RealProviderNotActiveException.class)
                .hasMessageContaining("REAL_PROVIDER_NOT_ACTIVE: Location is blank");
    }

    @Test
    @DisplayName("Test E: RUN_PHASE5_REAL_EVAL=true passes with valid real provider provenance")
    void testE_RealModeOnAcceptsValidRealProvider() {
        System.setProperty("RUN_PHASE5_REAL_EVAL", "true");

        EvaluationProviderProvenance realProv = new EvaluationProviderProvenance(
                true, "Vertex AI", "gemini-2.5-flash-lite", "finsight-ai-dev", "us-central1", "com.finsight.VertexChatAdapter", "CLOUD_REAL_VERTEX"
        );

        assertThatCode(() -> RealProviderGate.verifyGate(realProv))
                .doesNotThrowAnyException();
    }
}
