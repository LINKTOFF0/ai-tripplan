package routeMakingAgent.agents;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.tool.Toolkit;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import routeMakingAgent.mcp.BaiduMapMCP;
import utils.AgentUtils;
import utils.ToolUtils;


@Component
public class RouteMakingAgent {

    @Bean
    public ReActAgent getRouteMakingAgent() {
        //Toolkit
        ToolUtils toolUtils = new ToolUtils();
        //将百度地图MCP注册到工具包
        Toolkit toolkit = toolUtils.getToolkit(new BaiduMapMCP());

        //路线规划Agent Builder
        return AgentUtils.getReActAgentBuilder(
                        "RouteMakingAgent",
                        "路线规划Agent"
                ).toolkit(toolkit).build();
    }
}
