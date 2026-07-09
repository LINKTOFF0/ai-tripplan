package routeMakingAgent.agents;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import routeMakingAgent.mcp.BaiduMapMCP;
import utils.AgentUtils;
import utils.ToolUtils;

import java.util.Set;

@Component
@Slf4j
public class RouteMakingAgent {

    @Value("${mcp.baidu_map_addr:#{null}}")
    private String baiduMapAddr;

    @Bean
    public ReActAgent getRouteMakingAgent() {

        // 将 Spring 属性注入到系统属性，供 BaiduMapMCP 读取
        if (baiduMapAddr != null && !baiduMapAddr.isBlank()) {
            System.setProperty("BAIDU_MAP_ADDR", baiduMapAddr);
        }

        BaiduMapMCP mcp = new BaiduMapMCP();
        //创建百度地图MCP客户端
        mcp.getBaiduMapMCP();
        //初始化百度地图MCP客户端
        McpClientWrapper mcpClient = mcp.initBaiduMapMCP();

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
                        "擅长处理自驾游路线制定"
                )
                //工具包
                .toolkit(toolkit)
                .build();
    }
}
