package com.platform.llm.gateway;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.ModelType;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;

public class LLmGateway {

    private final ChatLanguageModel cheapModel;
    private final StringRedisTemplate redis;
    private final MeterRegistry metrics;
    private final Encoding tokenEncoder;

    public LLmGateway(@Qualifier("cheapModel") ChatLanguageModel cheapModel,
                      StringRedisTemplate redis,
                      MeterRegistry metrics){
        this.cheapModel = cheapModel;
        this.redis = redis;
        this.metrics = metrics;
        EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
        this.tokenEncoder = registry.getEncodingForModel(ModelType.GPT_4O);
    }

    public String chat(String prompt, String agentName){
        String cacheKey = "llm:resp:" + sha256(prompt);
        long startTime = System.nanoTime();

        String cachedResponse = redis.opsForValue().get(cacheKey);
        if (cachedResponse != null){
            metrics.counter("llm.calls_total", "model", "gemma2", "agent", agentName, "cache", "hit").increment();
            return cachedResponse;
        }
        metrics.counter("llm.calls_total", "model", "gemma2", "agent", agentName, "cache", "miss").increment();

        int estimatedTokens = tokenEncoder.countTokens(prompt);

        Response<AiMessage> modelResponse;

        try {
            modelResponse = Timer.builder("llm.latency_seconds")
                .tags("model", "gemma2", "agent", agentName)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(metrics)
                .recordCallable(() -> cheapModel.generate(UserMessage.from(prompt)));
        } catch (Exception e) {
            throw new RuntimeException("Downstream structural model processing error", e);
        }

        int actualTokensIn = modelResponse.tokenUsage().inputTokenCount();
        int actualTokensOut = modelResponse.tokenUsage().outputTokenCount();
        metrics.counter("llm.tokens_in_total", "model", "gemma2").increment(actualTokensIn);
        metrics.counter("llm.tokens_out_total", "model", "gemma2").increment(actualTokensOut);

        // Local calculation verification mismatch delta metrics tracked here
        metrics.gauge("llm.tokens.estimation_delta", actualTokensIn - estimatedTokens);

        String resultText = modelResponse.content().text();

        // 5. Short-term Cache Commit
        redis.opsForValue().set(cacheKey, resultText, Duration.ofHours(24));

        return resultText;
    }

    private String sha256(String base) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(base.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }


}
