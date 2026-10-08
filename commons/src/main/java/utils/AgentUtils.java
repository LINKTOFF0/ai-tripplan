package utils;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.AgentBase;
import io.agentscope.core.agent.Event;
import io.agentscope.core.formatter.openai.DeepSeekFormatter;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.OpenAIChatModel;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

public class AgentUtils {
    public static ReActAgent.Builder getReActAgentBuilder(String name, String description) {
        String provider = getConfig("LLM_PROVIDER", "deepseek");
        if (!"deepseek".equalsIgnoreCase(provider)) {
            throw new IllegalStateException("当前仅支持 LLM_PROVIDER=deepseek");
        }

        return ReActAgent.builder()
                .name(name)
                .description(description)
                .sysPrompt(description)
                .model(OpenAIChatModel.builder()
                        .apiKey(requireConfig("DEEPSEEK_API_KEY"))
                        .baseUrl(getConfig("DEEPSEEK_BASE_URL", "https://api.deepseek.com"))
                        .modelName(getConfig("DEEPSEEK_MODEL", "deepseek-chat"))
                        .formatter(new DeepSeekFormatter())
                        .generateOptions(GenerateOptions.builder()
                                .additionalBodyParam("thinking", Map.of("type", "disabled"))
                                .build())
                        .stream(false)
                        .build());
    }

    private static String getConfig(String name, String fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) value = System.getProperty(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String requireConfig(String name) {
        String value = getConfig(name, "");
        if (value.isBlank()) throw new IllegalStateException("缺少必需配置: " + name);
        return value;
    }

    public static Flux<Event> streamResponse(AgentBase agent, String prompt) {
        return agent.stream(userMessage(prompt));
    }

    public static <T> Msg callWithStructuredOutput(AgentBase agent, String prompt, Class<T> outputClass) {
        return agent.call(List.of(userMessage(prompt)), outputClass).block();
    }

    public static Msg userMessage(String prompt) {
        return Msg.builder().role(MsgRole.USER)
                .content(List.of(TextBlock.builder().text(prompt).build())).build();
    }
}
