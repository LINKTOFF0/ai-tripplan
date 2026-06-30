package routeMakingAgent.mcp;

import io.agentscope.core.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
public class BaiduMapMCP {
    @Tool(description = "百度地图MCP Server")
    public void getBaiduMapMcp() {
        log.info("==================");
        log.info("正在调用百度地图MCP....");
        log.info("==================");
    }
}
