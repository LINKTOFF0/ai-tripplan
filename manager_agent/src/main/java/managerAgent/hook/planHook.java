package managerAgent.hook;

import io.agentscope.core.agent.user.UserAgent;
import io.agentscope.core.hook.*;
import io.agentscope.core.plan.PlanNotebook;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;


@Slf4j
public class planHook implements Hook {

    //监听用户输入
    private final UserAgent user;
    //计划步骤
    private final PlanNotebook plan;

    public planHook(PlanNotebook planNotebook) {
        this.user = UserAgent.builder()
                .name("User")
                .build();
        this.plan = planNotebook;
    }

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        //匹配不同的事件
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
                if (reason != null) {
                    log.info("#######思考过程：#######");
                    log.info(reason);
                    log.info("######################");
                }
            }
/*                //当计划列表已生成
                Plan currentPlan = plan.getCurrentPlan();
                if (currentPlan != null) {
                    System.out.println("请输入修改意见（10秒内无输入则自动继续）: ");
                    user.call()
                            .timeout(Duration.ofSeconds(10))
                            .onErrorResume(TimeoutException.class, err -> {
                                System.out.println("超时未收到输入，自动继续执行...");
                                return Mono.empty();
                            })
                            .block();
                }
            }*/
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
