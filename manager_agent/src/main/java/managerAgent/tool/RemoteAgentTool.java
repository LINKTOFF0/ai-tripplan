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

    @Tool(description = "路线规划专家，提供：驾车/铁路/飞机路线、距离、耗时、交通方式对比。必须调用此工具获取任何交通路线信息，严禁自行编造车次和航班或路线")
    public String callRouteMakingAgent(
            @ToolParam(name = "prompt",description = "路线查询需求，包含起点、终点、出行方式（铁路/自驾/飞机等）")
            String prompt) throws NacosException {
        log.info("============");
        log.info("工具方法：路线制定智能体...正在调用中");
        log.info("============");

        if (prompt == null || prompt.isBlank()) {
            log.warn("callRouteMakingAgent 收到空 prompt，跳过调用");
            return "无法制定路线：未收到有效的起终点信息";
        }

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

    @Tool(description = "行程规划专家，提供：每日景点安排、美食推荐、住宿建议、天气参考。获取路线后必须调用此工具完成行程细节规划")
    public String callTripPlannerAgent(
            @ToolParam(name = "prompt",description = "行程规划需求，包含目的地、天数、偏好、路线信息等")
            String prompt) throws NacosException {
        log.info("============");
        log.info("工具方法：行程规划智能体...正在调用中");
        log.info("============");

        if (prompt == null || prompt.isBlank()) {
            log.warn("callTripPlannerAgent 收到空 prompt，跳过调用");
            return "无法规划行程：未收到有效的行程需求信息";
        }

        A2aAgent agent = A2aAgent.builder()
                .name("TripPlannerAgent")
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
}
