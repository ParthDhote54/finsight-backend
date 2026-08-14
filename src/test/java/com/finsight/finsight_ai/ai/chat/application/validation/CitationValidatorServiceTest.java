package com.finsight.finsight_ai.ai.chat.application.validation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CitationValidatorServiceTest {

    private final CitationValidatorService validator = new CitationValidatorService();

    @Test
    void acceptsExplicitEvidenceAndDeduplicatesInNarrativeOrder() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        var result = validator.validate(first + " then " + second + " and " + first,
                Set.of(first, second));

        assertThat(result.hallucinationDetected()).isFalse();
        assertThat(result.validCitations()).containsExactly(first, second);
    }

    @Test
    void rejectsAndRemovesUnsupportedUppercaseUuid() {
        UUID unsupported = UUID.randomUUID();

        var result = validator.validate(
                "Transaction " + unsupported.toString().toUpperCase() + " is not evidence.",
                List.of());

        assertThat(result.hallucinationDetected()).isTrue();
        assertThat(result.invalidCitations()).containsExactly(unsupported);
        assertThat(result.cleanedText()).contains("[citation removed]").doesNotContain(unsupported.toString());
    }

    @Test
    void emptyEvidenceAllowsNoArbitraryEntityIdentifier() {
        UUID userId = UUID.randomUUID();
        assertThat(validator.validate("User " + userId, null).hallucinationDetected()).isTrue();
    }
}
