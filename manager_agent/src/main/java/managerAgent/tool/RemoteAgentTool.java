package managerAgent.tool;


import com.alibaba.nacos.api.exception.NacosException;
import io.agentscope.core.a2a.agent.A2aAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.nacos.a2a.discovery.NacosAgentCardResolver;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import utils.NacosUtil;
import utils.PromptUtils;


@Slf4j
public class RemoteAgentTool {

    @Tool(description = "擅长制定最优出游路线的Agent")
    public String callRouteMakingAgent(
            @ToolParam(name = "prompt",description = "行程的起点和终点")
            String prompt) throws NacosException {
        log.info("============");
        log.info("工具方法：路线制定智能体...正在调用中");
        log.info("============");

        A2aAgent agent = A2aAgent.builder()
                .name("RouteMakingAgent")
                .agentCardResolver(
                        //创建 Nacos 的 AgentCardResolver
                        new NacosAgentCardResolver(NacosUtil.getNacosClient()))
                .build();

        log.info("============");
        log.info("获取到的远程Agent描述："+agent.getDescription());
        log.info("============");

        log.info("============");
        log.info("这个工具方法传入的参数：" + prompt);
        log.info("============");

        PromptUtils promptUtils = new PromptUtils();
        Msg userMsg = promptUtils.getUserMsg(prompt);
        //远程Agent运行
        Msg remoteAgentResponse = agent.call(userMsg).block();
        return remoteAgentResponse.getTextContent();
    }

    @Tool(description = "从Nacos注册中心获取行程规划Agent")
    public void callTripPlannerAgent() throws NacosException {

        A2aAgent agent = A2aAgent.builder()
                .name("TripPlannerAgent")
                .agentCardResolver(
                        //创建 Nacos 的 AgentCardResolver
                        new NacosAgentCardResolver(NacosUtil.getNacosClient()))
                .build();
        //远程Agent运行
        agent.call().block();

    }
}
