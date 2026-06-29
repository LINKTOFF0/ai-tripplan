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

/**
 * author: lin
 * description: ReActAgent 工具类
 * date: 2026
 */


public class AgentUtils {
    //创建ReActAgent Builder
    public static ReActAgent.Builder getReActAgentBuilder(
            String name,
            String description
    ) {

        return ReActAgent.builder()
                .name(name)
                .description(description)
                .model(DashScopeChatModel.builder()
                        //请求语言大模型的apikey
                        .apiKey("sk-ws-H.RYIPIIL.ZpRK.MEQCIBaTgOAjRXCr8E_kPzGWeA1BYdHDuEnZTcMMb7vDy21dAiBUxjvldq_P6WjqTk5TtnBKhZBdCFY4uuPKga3k1dwk1w")
                        //所使用的语言大模型
                        .modelName("qwen3-max")
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
