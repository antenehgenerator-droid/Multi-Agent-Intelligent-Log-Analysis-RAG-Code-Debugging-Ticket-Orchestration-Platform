package com.platform.llm.util;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.util.List;

public class DeterministicFakeModel implements ChatLanguageModel {
    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        String inputPrompt = messages.get(messages.size() - 1).text();
        String jsonPayloadResponse = "{\"cause\": \"UNHANDLED_MOCK_STATE\"}";

        if (inputPrompt.contains("NullPointerException")) {
            jsonPayloadResponse = "{\"file\":\"PaymentService.java\",\"method\":\"process\",\"issue\":\"Null pointer on parameter paymentId\",\"fix\":\"Add objects requireNonNull check\",\"confidence\":0.92}";
        } else if (inputPrompt.contains("TimeoutException")) {
            jsonPayloadResponse = "{\"file\":\"DatabasePool.java\",\"method\":\"getConnection\",\"issue\":\"Connection pool depletion\",\"fix\":\"Increase max pool sizes to 50\",\"confidence\":0.85}";
        }

        return Response.from(
            AiMessage.from(jsonPayloadResponse),
            new TokenUsage(inputPrompt.length() / 4, jsonPayloadResponse.length() / 4)
        );
    }
}
