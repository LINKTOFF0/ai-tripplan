package utils;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.AgentBase;
import io.agentscope.core.agent.Event;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.DashScopeChatModel;
import reactor.core.publisher.Flux;

import java.util.List;

//ReActAgent 工具类
public class AgentUtils {

    //创建ReActAgent Builder
    public static ReActAgent.Builder getReActAgentBuilder(
            String name,
            String description
    ) {

        // 从环境变量/系统属性读取 API Key 和模型名称，避免硬编码
        // 优先级：OS 环境变量 > 系统属性（-D 参数或 .env 加载）
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getProperty("DASHSCOPE_API_KEY", "your-api-key-placeholder");
        }
        String modelName = System.getenv("MODEL_NAME");
        if (modelName == null || modelName.isBlank()) {
            modelName = System.getProperty("MODEL_NAME", "qwen3-max");
        }

        return ReActAgent.builder()
                .name(name)
                .description(description)
                .model(DashScopeChatModel.builder()
                        //请求语言大模型的apikey
                        .apiKey(apiKey)
                        //所使用的语言大模型
                        .modelName(modelName)
                        .stream(true)
                        .build())
                ;

    }

    //ReActAgent流式响应
    public static Flux<Event> streamResponse(
            AgentBase agent,
            String prompt) {

        return agent.stream(
                //Prompt
                Msg.builder()
                        //消息角色
                        .role(MsgRole.USER)
                        //消息内容 (Prompt)
                        .content(List.of(
                                TextBlock.builder()
                                        .text(prompt)
                                        .build()
                        ))
                        .build()
        );
    }

    //ReActAgent结构化输出调用
    public static <T> Msg callWithStructuredOutput(
            AgentBase agent,
            String prompt,
            Class<T> outputClass) {

        return agent.call(
                        //消息列表
                        List.of(Msg.builder()
                                .role(MsgRole.USER)
                                .content(List.of(
                                        TextBlock.builder()
                                                .text(prompt)
                                                .build()
                                ))
                                .build()),
                        //结构化输出类型
                        outputClass)
                .block();
    }
}
