package routeMakingAgent.mcp;

import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Optional;


@Slf4j
public class BaiduMapMCP {

    //MCP 客户端
    private McpClientWrapper baiduMapMCP = null;
    //MCP 客户端初始化
    private boolean mcpInitialized = false;

    /**
     * 获取 MCP Server 地址，优先级：系统环境变量 > 系统属性 > 默认值
     */
    private String getMcpAddr() {
        // 1. 优先从 OS 环境变量读取
        String addr = System.getenv("BAIDU_MAP_ADDR");
        if (addr != null && !addr.isBlank()) {
            log.info("从环境变量 BAIDU_MAP_ADDR 读取 MCP 地址: {}", addr);
            return addr;
        }
        // 2. 回退到系统属性（可通过 -D 参数或 Spring 配置注入）
        addr = System.getProperty("BAIDU_MAP_ADDR");
        if (addr != null && !addr.isBlank()) {
            log.info("从系统属性 BAIDU_MAP_ADDR 读取 MCP 地址: {}", addr);
            return addr;
        }
        // 3. 默认值（会报错，提示用户配置）
        throw new IllegalStateException(
                "未配置 BAIDU_MAP_ADDR！请设置环境变量 BAIDU_MAP_ADDR 或通过 -DBAIDU_MAP_ADDR=<url> 启动应用");
    }

    //@Tool(description = "百度地图MCP Server")
    public void getBaiduMapMCP() {

        String mcpAddr = getMcpAddr();

        log.info("正在连接百度地图 MCP Server: {}", mcpAddr);

        //创建MCP客户端
        baiduMapMCP = McpClientBuilder.create("BaiduMap-mcp")
                //和MCP Server以SSE方式进行通信
                .sseTransport(mcpAddr)
                //请求超时
                .timeout(Duration.ofSeconds(120))
                //异步请求
                .buildAsync()
                .block();

    }

    public McpClientWrapper initBaiduMapMCP() {

        //通过Optional判断百度MCP客户端是否为null
        Optional<McpClientWrapper> mcpClientWrapper = Optional.ofNullable(baiduMapMCP);
        if(mcpClientWrapper.isPresent()) {
            log.info("==================");
            log.info("百度MCP客户端已经创建");
            log.info("==================");

            if(!mcpInitialized) {
                synchronized (this) {
                    if (!mcpInitialized) {

                        //MCP客户端初始化
                        baiduMapMCP.initialize().block();

                        //获取MCP服务端工具列表
                        if(baiduMapMCP.isInitialized()) {

                            log.info("=============");
                            log.info("百度地图MCP 客户端初始化成功！");
                            log.info("=============");

                            baiduMapMCP.listTools().block().forEach(tool -> {
                                log.info("==================");
                                log.info("百度地图MCP工具列表：" + tool.name());
                                log.info("==================");
                            });

                            mcpInitialized=true;
                        }

                    }
                }
            }

        }
        return baiduMapMCP;
    }
}
