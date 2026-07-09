package utils;

import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientWrapper;

public class ToolUtils {
    private final Toolkit toolkit;

    public ToolUtils() {
        //创建工具包
        toolkit = new Toolkit();
    }

    //获取工具包
    public Toolkit getToolkit(Object tool) {

        //把工具添加到工具包，能自动扫描@Tool所注释的方法，作为Agent的工具
        toolkit.registerTool(tool);

        return toolkit;
    }

    //获取工具包
    public Toolkit getToolkit(McpClientWrapper mcp) {

        //把MCP服务端的所有工具添加到工具包
        toolkit.registerMcpClient(mcp).block();

        return toolkit;
    }
}
