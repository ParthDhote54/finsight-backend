package com.finsight.finsight_ai.ai.chat.support;

public final class UserPromptContext {
    private static final ThreadLocal<String> PROMPT_HOLDER = new ThreadLocal<>();

    private UserPromptContext() {}

    public static void set(String prompt) {
        PROMPT_HOLDER.set(prompt);
    }

    public static String get() {
        return PROMPT_HOLDER.get();
    }

    public static void clear() {
        PROMPT_HOLDER.remove();
    }
}
