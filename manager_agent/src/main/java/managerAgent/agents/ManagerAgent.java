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
                // .enablePlan()
                .planNotebook(planNotebook)
                //拦截器
                .hook(new planHook(planNotebook))
                //工具包
                .toolkit(toolkit)
                .build();
    }

    public void run() {
        String prompt =
                """
                           帮我制定2026年国庆节，
                        泉州到深圳的7天旅行计划，
                        请包含吃住行，天气，酒店，餐饮美食。
                        -从Nacos注册中心获取远程Agent，
                        -把子任务分发给擅长处理相关任务的Agent
                        -每个子任务注明调用的Agent
                        
                        """;


        Flux<Event> stream = AgentUtils.streamResponse(agent, prompt);

        //把响应结打印出来
        stream
                .doOnNext(msg -> System.out.println(msg.getMessage().getTextContent()))
                //阻塞直到结束
                .blockLast();
    }

}
