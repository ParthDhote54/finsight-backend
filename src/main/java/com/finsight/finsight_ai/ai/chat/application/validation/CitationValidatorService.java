package com.finsight.finsight_ai.ai.chat.application.validation;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Validates transaction citations against an explicit server-side evidence allowlist. */
@Component
public class CitationValidatorService {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "\\b([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})\\b");

    public CitationValidationResult validate(String textAnswer, Collection<UUID> validIds) {
        if (textAnswer == null || textAnswer.isBlank()) {
            return new CitationValidationResult(textAnswer, false, List.of(), List.of());
        }

        Set<UUID> allowlist = validIds == null
                ? Collections.emptySet()
                : Set.copyOf(validIds);
        Set<UUID> valid = new LinkedHashSet<>();
        Set<UUID> invalid = new LinkedHashSet<>();
        Matcher matcher = UUID_PATTERN.matcher(textAnswer);
        StringBuffer cleaned = new StringBuffer();

        while (matcher.find()) {
            UUID cited;
            try {
                cited = UUID.fromString(matcher.group(1));
            } catch (IllegalArgumentException exception) {
                matcher.appendReplacement(cleaned, Matcher.quoteReplacement("[citation removed]"));
                continue;
            }

            if (allowlist.contains(cited)) {
                valid.add(cited);
                matcher.appendReplacement(cleaned, Matcher.quoteReplacement(matcher.group()));
            } else {
                invalid.add(cited);
                matcher.appendReplacement(cleaned, Matcher.quoteReplacement("[citation removed]"));
            }
        }
        matcher.appendTail(cleaned);

        return new CitationValidationResult(
                cleaned.toString(),
                !invalid.isEmpty(),
                List.copyOf(valid),
                List.copyOf(invalid));
    }

    public record CitationValidationResult(
            String cleanedText,
            boolean hallucinationDetected,
            List<UUID> validCitations,
            List<UUID> invalidCitations
    ) {
        public CitationValidationResult {
            validCitations = validCitations == null ? List.of() : List.copyOf(validCitations);
            invalidCitations = invalidCitations == null ? List.of() : List.copyOf(invalidCitations);
        }
    }
}
