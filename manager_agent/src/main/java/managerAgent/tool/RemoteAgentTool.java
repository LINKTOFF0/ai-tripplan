package managerAgent.tool;

import com.alibaba.nacos.api.exception.NacosException;
import io.agentscope.core.a2a.agent.A2aAgent;
import io.agentscope.core.nacos.a2a.discovery.NacosAgentCardResolver;
import io.agentscope.core.tool.Tool;
import utils.NacosUtil;

//将远程智能体卡片封装为工具
public class RemoteAgentTool {
    //基于A2A协议获取路线制定Agent
    @Tool(description = "从Nacos注册中心获取路线制定Agent")
    public void callRouteMakingAgent() throws NacosException {


        A2aAgent agent = A2aAgent.builder()
                .name("RouteMakingAgent")
                .agentCardResolver(
                        //创建 Nacos 的 AgentCardResolver
                        new NacosAgentCardResolver(NacosUtil.getNacosClient()))
                .build();
        //远程Agent运行
        agent.call().block();
    }

    //基于A2A协议获取行程规划Agent
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
