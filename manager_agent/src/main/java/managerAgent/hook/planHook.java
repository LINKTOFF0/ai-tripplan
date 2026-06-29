package managerAgent.hook;

import io.agentscope.core.hook.*;
import io.agentscope.core.plan.model.Plan;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

//计划拦截器
@Slf4j
public class planHook implements Hook {
    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {

        switch (event) {

            //用户输入事件
            case PreReasoningEvent e -> {

                String reason = e.getInputMessages().get(0).getTextContent();
                log.info("#### 用户的Prompt：#######" );
                log.info(reason);

            }

            //推理思考事件
            case PostReasoningEvent e -> {

                String reason = e.getReasoningMessage().getTextContent();
                log.info("#### 思考过程：#######" );
                log.info(reason);
            }


            //调用工具事件
            case PostActingEvent e -> {

                String toolName = e.getToolUse().getName();
                log.info("##### 调用工具："+toolName);
            }

            default -> {
                // 其他事件忽略
            }
        }

        // 返回原事件
        return Mono.just(event);
    }
}
