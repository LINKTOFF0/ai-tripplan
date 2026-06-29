package managerAgent.agents;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Event;
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

        agent = AgentUtils.getReActAgentBuilder(
                        "ManagerAgent",
                        "主管Agent"
                )
                //.enablePlan()
                .planNotebook(plan.getPlan())
                //拦截器
                .hook(new planHook())
                .toolkit(toolkit)
                .build();
    }

    public void run() {
        String prompt = """
                   帮我制定2026年元旦,
                   深圳到惠州3日游自驾游计划，
                   请包含吃住行，天气，酒店，餐饮美食。
                
                   你可以调用以下Agent处理子任务
                   - routeMaking Agent：擅长处理自驾游路线制定
                   - tripPlanner Agent：擅长处理景点行程规划
                
                   -每个子任务要注明调用的Agent
                
                """;
        Flux<Event> stream = AgentUtils.streamResponse(agent, prompt);
        stream.doOnNext(msg -> System.out.println(msg.getMessage().getContent()))
                .blockLast();
    }
}
