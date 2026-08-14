package com.finsight.finsight_ai.ai.chat.ports.out;

import com.finsight.finsight_ai.ai.chat.domain.ChatModelInput;
import com.finsight.finsight_ai.ai.chat.domain.ChatModelOutput;
public interface ChatModelPort {

    /*
    Executes a generation request against the configured LLM provider.
    *
    *@param input the structured prompt , history and available tool specifications.
    @return ChatModelOutput containing either tool calls requests or final text answer.
     */

    ChatModelOutput generate(ChatModelInput input);
}



