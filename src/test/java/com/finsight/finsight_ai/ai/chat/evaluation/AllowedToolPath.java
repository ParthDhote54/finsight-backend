package com.finsight.finsight_ai.ai.chat.evaluation;

import java.util.List;

public record AllowedToolPath(
        List<String> orderedTools,
        boolean allowAdditionalAllowedTools
) {
    public AllowedToolPath {
        orderedTools = orderedTools == null ? List.of() : List.copyOf(orderedTools);
    }

    public static AllowedToolPath exact(String... orderedTools) {
        return new AllowedToolPath(List.of(orderedTools), false);
    }

    public static AllowedToolPath allowingAdditional(String... orderedTools) {
        return new AllowedToolPath(List.of(orderedTools), true);
    }

    boolean matches(List<String> actualTools) {
        List<String> actual = actualTools == null ? List.of() : actualTools;
        if (!allowAdditionalAllowedTools) {
            return actual.equals(orderedTools);
        }
        int cursor = 0;
        for (String actualTool : actual) {
            if (cursor < orderedTools.size() && orderedTools.get(cursor).equals(actualTool)) {
                cursor++;
            }
        }
        return cursor == orderedTools.size();
    }
}
