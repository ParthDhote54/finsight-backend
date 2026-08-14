package com.finsight.finsight_ai.ai.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;


@Slf4j
@RequiredArgsConstructor
public class chatService {
    private final ChatClient chatClient;

    /*
    *Answers a user's financial question using their actual database records.
    *@param userQuestion the question(e.g., "what is my biggest expense")
    *@param transactionData The raw data we retrieved from PostgresSQL.
    * @return The AI's intelligent Power.
    *
     */

    public String answerFinancialQuestion(String userQuestion, String transactionData) {
        log.info("event=PROCESSING_RAG_QUERY | question = '{}'", userQuestion);
        String systemPrompt = """
                You are Finsight, a highly intelligent and helpful personal finance assistant.
                You are helping a user understand their spending habits.
               
                RULES:
                1.You must base your answers STRICTLY on the Transaction Data provided below.
                2.Do not hallucinate or guess. If the data doesn't contain the answer, politely say so.
                3.Keep your answer concise, professional, and friendly.
          
                TRANSACTION DATA TO ANALYSE : %s
               """.formatted(transactionData);

        try{
            return chatClient.prompt()
                    .system(systemPrompt) //The hidden context.
                    .user(userQuestion)   //The actual question.
                    .call()
                    .content();
       }
        catch (Exception ex) {
            log.error("event = RAG_QUERY_FAILED | question = '{}'", userQuestion, ex);
            return "I'm having trouble analyzing your data right now. Please try again in a moment";
        }
    }
}
