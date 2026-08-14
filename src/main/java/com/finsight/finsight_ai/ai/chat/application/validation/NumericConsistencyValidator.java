package com.finsight.finsight_ai.ai.chat.application.validation;

import com.finsight.finsight_ai.ai.chat.domain.NumericEvidence;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates numeric claims against typed evidence emitted by deterministic finance tools.
 * It deliberately never scans serialized tool JSON: identifiers, dates, counts and ranks
 * are not interchangeable with monetary evidence.
 */
@Component
public class NumericConsistencyValidator {

    private static final String NUMBER = "[+-]?\\d+(?:,\\d{3})*(?:\\.\\d+)?";
    private static final Pattern UUID = Pattern.compile(
            "(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b");
    private static final Pattern ISO_DATE = Pattern.compile(
            "\\b\\d{4}-\\d{2}(?:-\\d{2})?\\b|\\b\\d{1,2}/\\d{1,2}/\\d{2,4}\\b");
    private static final String MONTH = "(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|"
            + "jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)";
    private static final Pattern MONTH_DATE = Pattern.compile(
            "(?i)\\b" + MONTH + "(?:\\s+\\d{1,2}(?:st|nd|rd|th)?)?(?:,?\\s+\\d{4})?\\b");
    private static final Pattern CONTEXTUAL_YEAR = Pattern.compile(
            "(?i)\\b(?:in|during|for|year)\\s+(?:the\\s+year\\s+)?(?:19|20)\\d{2}\\b");
    private static final Pattern LIST_MARKER = Pattern.compile("(?m)^\\s*\\d+[.)]\\s+");
    private static final Pattern INLINE_LIST_MARKER = Pattern.compile(
            "(?i)(?:(?<=^)|(?<=[\\s:;]))\\d+[.)]\\s+");
    private static final Pattern ORDINAL_LABEL = Pattern.compile(
            "(?i)\\b(?:transaction|transactions|item|items|result|results|record|records)\\s+#?\\d+\\b");
    private static final Pattern MONEY_SYMBOL = Pattern.compile(
            "(?i)([₹$€£])\\s*(" + NUMBER + ")|(" + NUMBER + ")\\s*(INR|USD|EUR|GBP)\\b|"
                    + "\\b(INR|USD|EUR|GBP)\\s*(" + NUMBER + ")");
    private static final Pattern PERCENTAGE = Pattern.compile(
            "(?i)(" + NUMBER + ")\\s*%(?:\\s*(increase|decrease|higher|lower|up|down))?");
    private static final Pattern COUNT = Pattern.compile(
            "(?i)\\b(" + NUMBER + ")\\s+(transactions?|purchases?|items?|merchants?|categories?|results?|records?)\\b");
    private static final Pattern RANK = Pattern.compile(
            "(?i)#\\s*(\\d+)|\\brank(?:ed)?\\s*(?:is|:|#)?\\s*(\\d+)\\b");
    private static final Pattern BARE_NUMBER = Pattern.compile("(?<![\\p{L}\\p{N}_])(" + NUMBER + ")(?![\\p{L}\\p{N}_])");

    public ValidationResult validate(String answer, Collection<NumericEvidence> evidence) {
        if (answer == null || answer.isBlank()) {
            return new ValidationResult(true, List.of(), List.of());
        }

        List<NumericClaim> claims = extractClaims(answer);
        List<NumericEvidence> safeEvidence = evidence == null ? List.of() : List.copyOf(evidence);
        List<GroundedClaim> grounded = new ArrayList<>();
        List<NumericClaim> unsupported = new ArrayList<>();

        for (NumericClaim claim : claims) {
            Optional<NumericEvidence> match = safeEvidence.stream()
                    .filter(item -> matches(claim, item))
                    .findFirst();
            if (match.isPresent()) {
                grounded.add(new GroundedClaim(claim, match.get()));
            } else {
                unsupported.add(claim);
            }
        }

        return new ValidationResult(unsupported.isEmpty(), unsupported, grounded);
    }

    private static boolean matches(NumericClaim claim, NumericEvidence evidence) {
        if (claim.kind() != evidence.kind() || claim.value().compareTo(evidence.value()) != 0) {
            return false;
        }
        return claim.currency() == null || claim.currency().equals(evidence.currency());
    }

    private static List<NumericClaim> extractClaims(String input) {
        String text = Normalizer.normalize(input, Normalizer.Form.NFKC);
        boolean[] consumed = new boolean[text.length()];
        mask(UUID, text, consumed);
        mask(ISO_DATE, text, consumed);
        mask(MONTH_DATE, text, consumed);
        mask(CONTEXTUAL_YEAR, text, consumed);
        mask(LIST_MARKER, text, consumed);
        mask(INLINE_LIST_MARKER, text, consumed);
        mask(ORDINAL_LABEL, text, consumed);

        List<NumericClaim> claims = new ArrayList<>();
        extractMoney(text, consumed, claims);
        extractPercentage(text, consumed, claims);
        extractSimple(PatternType.COUNT, COUNT, text, consumed, claims);
        extractSimple(PatternType.RANK, RANK, text, consumed, claims);

        Matcher bare = BARE_NUMBER.matcher(text);
        while (bare.find()) {
            if (available(consumed, bare.start(), bare.end())) {
                claims.add(new NumericClaim(
                        NumericEvidence.Kind.MONETARY,
                        decimal(bare.group(1)),
                        null,
                        bare.group()));
                mark(consumed, bare.start(), bare.end());
            }
        }
        return List.copyOf(claims);
    }

    private static void extractMoney(String text, boolean[] consumed, List<NumericClaim> claims) {
        Matcher matcher = MONEY_SYMBOL.matcher(text);
        while (matcher.find()) {
            if (!available(consumed, matcher.start(), matcher.end())) {
                continue;
            }
            String currencyCode;
            String value;
            if (matcher.group(1) != null) {
                currencyCode = switch (matcher.group(1)) {
                    case "₹" -> "INR";
                    case "$" -> "USD";
                    case "€" -> "EUR";
                    case "£" -> "GBP";
                    default -> throw new IllegalStateException("Unsupported currency symbol");
                };
                value = matcher.group(2);
            } else if (matcher.group(3) != null) {
                value = matcher.group(3);
                currencyCode = matcher.group(4).toUpperCase(Locale.ROOT);
            } else {
                currencyCode = matcher.group(5).toUpperCase(Locale.ROOT);
                value = matcher.group(6);
            }
            claims.add(new NumericClaim(
                    NumericEvidence.Kind.MONETARY,
                    decimal(value),
                    Currency.getInstance(currencyCode),
                    matcher.group()));
            mark(consumed, matcher.start(), matcher.end());
        }
    }

    private static void extractPercentage(String text, boolean[] consumed, List<NumericClaim> claims) {
        Matcher matcher = PERCENTAGE.matcher(text);
        while (matcher.find()) {
            if (!available(consumed, matcher.start(), matcher.end())) {
                continue;
            }
            BigDecimal value = decimal(matcher.group(1));
            String direction = matcher.group(2);
            if (direction != null
                    && (direction.equalsIgnoreCase("decrease")
                    || direction.equalsIgnoreCase("lower")
                    || direction.equalsIgnoreCase("down"))
                    && value.signum() > 0) {
                value = value.negate();
            }
            claims.add(new NumericClaim(
                    NumericEvidence.Kind.PERCENTAGE, value, null, matcher.group()));
            mark(consumed, matcher.start(), matcher.end());
        }
    }

    private static void extractSimple(PatternType type, Pattern pattern, String text,
                                      boolean[] consumed, List<NumericClaim> claims) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            if (!available(consumed, matcher.start(), matcher.end())) {
                continue;
            }
            String number = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            claims.add(new NumericClaim(
                    type == PatternType.COUNT ? NumericEvidence.Kind.COUNT : NumericEvidence.Kind.RANK,
                    decimal(number), null, matcher.group()));
            mark(consumed, matcher.start(), matcher.end());
        }
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value.replace(",", ""));
    }

    private static void mask(Pattern pattern, String text, boolean[] consumed) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            mark(consumed, matcher.start(), matcher.end());
        }
    }

    private static boolean available(boolean[] consumed, int start, int end) {
        for (int index = start; index < end; index++) {
            if (consumed[index]) {
                return false;
            }
        }
        return true;
    }

    private static void mark(boolean[] consumed, int start, int end) {
        for (int index = start; index < end; index++) {
            consumed[index] = true;
        }
    }

    private enum PatternType { COUNT, RANK }

    public record NumericClaim(
            NumericEvidence.Kind kind,
            BigDecimal value,
            Currency currency,
            String token
    ) {}

    public record GroundedClaim(NumericClaim claim, NumericEvidence evidence) {}

    public record ValidationResult(
            boolean valid,
            List<NumericClaim> unsupportedClaims,
            List<GroundedClaim> groundedClaims
    ) {
        public ValidationResult {
            unsupportedClaims = unsupportedClaims == null ? List.of() : List.copyOf(unsupportedClaims);
            groundedClaims = groundedClaims == null ? List.of() : List.copyOf(groundedClaims);
        }
    }
}
