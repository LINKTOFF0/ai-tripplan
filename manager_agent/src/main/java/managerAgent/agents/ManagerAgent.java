package managerAgent.agents;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.plan.PlanNotebook;
import io.agentscope.core.tool.Toolkit;
import managerAgent.hook.planHook;
import managerAgent.plan.TripPlan;
import managerAgent.tool.RemoteAgentTool;
import reactor.core.publisher.Flux;
import utils.AgentUtils;
import utils.ToolUtils;


public class ManagerAgent {
    private final ReActAgent agent;

    public ManagerAgent() {
        //PlanNotebook
        TripPlan plan = new TripPlan();
        //Toolkit
        ToolUtils toolUtils = new ToolUtils();
        //将远程Agent封装为工具的封装注册到工具包
        Toolkit toolkit = toolUtils.getToolkit(new RemoteAgentTool());
        //计划对象
        PlanNotebook planNotebook = plan.getPlan();

        agent = AgentUtils.getReActAgentBuilder(
                        "ManagerAgent",
                        "主管Agent"
                )
                //.enablePlan()
                .planNotebook(planNotebook)
                //拦截器
                .hook(new planHook())
                .toolkit(toolkit)
                .build();
    }

    public void run() {
        String prompt =
                """
                调用路线制定智能体
                """;
        Flux<Event> stream = AgentUtils.streamResponse(agent, prompt);
        stream.doOnNext(msg -> System.out.println(msg.getMessage().getContent()))
                .blockLast();
    }
}
