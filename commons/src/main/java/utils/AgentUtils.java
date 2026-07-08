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

        // 从环境变量读取 API Key 和模型名称，避免硬编码
        String apiKey = System.getenv().getOrDefault(
                "ALIBABA_DASHCOPE_KEY",
                "your-api-key-placeholder");
        String modelName = System.getenv().getOrDefault(
                "MODEL_NAME",
                "qwen3-max");

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
}
