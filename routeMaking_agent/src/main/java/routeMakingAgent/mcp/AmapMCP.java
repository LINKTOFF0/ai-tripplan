package routeMakingAgent.mcp;

import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Optional;


@Slf4j
public class AmapMCP {

    //MCP 客户端
    private McpClientWrapper amapMCP = null;
    //MCP 客户端初始化
    private boolean mcpInitialized = false;
    private String getMcpAddr() {
        // 1. 优先从 OS 环境变量读取
        String addr = System.getenv("AMAP_MAP_ADDR");
        if (addr != null && !addr.isBlank()) {
            log.info("从环境变量 AMAP_MAP_ADDR 读取 MCP 地址: {}", addr);
            return addr;
        }
        // 2. 回退到系统属性（可通过 -D 参数或 Spring 配置注入）
        addr = System.getProperty("AMAP_MAP_ADDR");
        if (addr != null && !addr.isBlank()) {
            log.info("从系统属性 AMAP_MAP_ADDR 读取 MCP 地址: {}", addr);
            return addr;
        }
        // 3. 未配置则报错
        throw new IllegalStateException(
                "未配置 AMAP_MAP_ADDR！请设置环境变量 AMAP_MAP_ADDR 或通过 -DAMAP_MAP_ADDR=<url> 启动应用。"
                        + "格式：https://mcp.amap.com/sse?key=你的高德Web服务Key");
    }

    //@Tool(description = "高德地图MCP Server")
    public void getAmapMCP() {

        String mcpAddr = getMcpAddr();

        log.info("正在连接高德地图 MCP Server: {}", mcpAddr);

        //创建MCP客户端
        amapMCP = McpClientBuilder.create("Amap-mcp")
                //和MCP Server以SSE方式进行通信
                .sseTransport(mcpAddr)
                //请求超时
                .timeout(Duration.ofSeconds(120))
                //异步请求
                .buildAsync()
                .block();

    }

    public McpClientWrapper initAmapMCP() {

        //通过Optional判断高德MCP客户端是否为null
        Optional<McpClientWrapper> mcpClientWrapper = Optional.ofNullable(amapMCP);
        if(mcpClientWrapper.isPresent()) {
            log.info("==================");
            log.info("高德MCP客户端已经创建");
            log.info("==================");

            if(!mcpInitialized) {
                synchronized (this) {
                    if (!mcpInitialized) {

                        //MCP客户端初始化
                        amapMCP.initialize().block();

                        //获取MCP服务端工具列表
                        if(amapMCP.isInitialized()) {

                            log.info("=============");
                            log.info("高德地图MCP 客户端初始化成功！");
                            log.info("=============");

                            amapMCP.listTools().block().forEach(tool -> {
                                log.info("==================");
                                log.info("高德地图MCP工具列表：" + tool.name());
                                log.info("==================");
                            });

                            mcpInitialized=true;
                        }

                    }
                }
            }

        }
        return amapMCP;
    }
}
