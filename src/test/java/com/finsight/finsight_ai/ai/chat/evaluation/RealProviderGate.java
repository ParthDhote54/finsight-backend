package com.finsight.finsight_ai.ai.chat.evaluation;

public class RealProviderGate {

    public static void verifyGate(EvaluationProviderProvenance provenance) {
        String realEvalProp = System.getProperty("RUN_PHASE5_REAL_EVAL", System.getenv("RUN_PHASE5_REAL_EVAL"));
        boolean strictRealMode = "true".equalsIgnoreCase(realEvalProp);

        if (!strictRealMode) {
            return; // Normal CI / test mode permitted
        }

        if (provenance == null) {
            throw new RealProviderNotActiveException("REAL_PROVIDER_NOT_ACTIVE: Provider provenance metadata is null");
        }

        if (!provenance.liveProvider()) {
            throw new RealProviderNotActiveException("REAL_PROVIDER_NOT_ACTIVE: Active model is not a live AI provider (liveProvider=false)");
        }

        if (provenance.implementationClass() != null &&
                (provenance.implementationClass().contains("Mock") ||
                        provenance.implementationClass().contains("Stub") ||
                        provenance.implementationClass().contains("Fake") ||
                        provenance.implementationClass().contains("Simulated"))) {
            throw new RealProviderNotActiveException("REAL_PROVIDER_NOT_ACTIVE: ChatModelPort implementation is mock/stub (" + provenance.implementationClass() + ")");
        }

        if (provenance.modelId() == null || provenance.modelId().isBlank()) {
            throw new RealProviderNotActiveException("REAL_PROVIDER_NOT_ACTIVE: Model ID is blank");
        }

        if (provenance.projectId() == null || provenance.projectId().isBlank()) {
            throw new RealProviderNotActiveException("REAL_PROVIDER_NOT_ACTIVE: Project ID is blank");
        }

        if (provenance.location() == null || provenance.location().isBlank()) {
            throw new RealProviderNotActiveException("REAL_PROVIDER_NOT_ACTIVE: Location is blank");
        }
    }
}
