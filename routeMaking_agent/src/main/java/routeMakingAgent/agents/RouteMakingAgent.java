package routeMakingAgent.agents;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import routeMakingAgent.mcp.BaiduMapMCP;
import utils.AgentUtils;
import utils.ToolUtils;

import java.util.Set;

@Slf4j
@Component
public class RouteMakingAgent {

    @Bean
    public ReActAgent getRouteMakingAgent() {

        BaiduMapMCP mcp = new BaiduMapMCP();
        //创建百度地图MCP客户端
        mcp.getBaiduMapMcp();
        //初始化百度地图MCP客户端
        McpClientWrapper mcpClient = mcp.initBaiduMapMCP();

        //Toolkit
        ToolUtils toolUtils = new ToolUtils();
        //将百度地图MCP注册到工具包
        Toolkit toolkit = toolUtils.getToolkit(mcpClient);

        //打印挂载工具
        Set<String> toolNames = toolkit.getToolNames();
        log.info("==========");
        toolNames.stream().forEach(
                value -> log.info("已挂载工具：" + value)
        );
        log.info("==========");

        //路线规划Agent Builder 注入Nacos
        return AgentUtils.getReActAgentBuilder(
                        "RouteMakingAgent",
                        "路线规划Agent"
                ).toolkit(toolkit).build();
    }
}
