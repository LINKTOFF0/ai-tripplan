package routeMakingAgent.agents;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import routeMakingAgent.mcp.AmapMCP;
import utils.AgentUtils;
import utils.ToolUtils;

import java.util.Set;

@Component
@Slf4j
public class RouteMakingAgent {

    @Value("${mcp.amap_addr:#{null}}")
    private String amapAddr;

    @Bean
    public ReActAgent getRouteMakingAgent() {

        // 将 Spring 属性注入到系统属性，供 AmapMCP 读取
        if (amapAddr != null && !amapAddr.isBlank()) {
            System.setProperty("AMAP_MAP_ADDR", amapAddr);
        }

        AmapMCP mcp = new AmapMCP();
        //创建高德地图MCP客户端
        mcp.getAmapMCP();
        //初始化高德地图MCP客户端
        McpClientWrapper mcpClient = mcp.initAmapMCP();

        //Toolkit
        ToolUtils toolUtils = new ToolUtils();
        Toolkit toolkit = toolUtils.getToolkit(mcpClient);

        //打印挂载的工具
        Set<String> toolNames = toolkit.getToolNames();
        log.info("=============");
        toolNames.stream().forEach(
                value -> log.info("挂载的工具名称："+value)
        );
        log.info("=============");

        //注入到Nacos
        return AgentUtils.getReActAgentBuilder(
                        "RouteMakingAgent",
                        "路线规划专家，可使用高德地图工具查询：驾车/铁路/公交/步行/骑行路线、距离耗时、地理编码、POI搜索、天气、IP定位。你有实时地图数据，必须提供具体的路线信息"
                )
                //工具包
                .toolkit(toolkit)
                .build();
    }
}
