package managerAgent.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import utils.MapTools;
import java.util.Map;
import java.util.Set;

public final class ScopedMapTool {
    private final MapTools delegate;
    private final Set<String> allowed;

    public ScopedMapTool(MapTools delegate, Set<String> allowed) {
        this.delegate = delegate; this.allowed = Set.copyOf(allowed);
    }

    @Tool(description = "查看当前任务允许的地图工具。category为place、route或weather。")
    public String listMapTools(@ToolParam(name = "category", description = "所需地图类别") String category) {
        return category != null && allowed.contains(category) ? delegate.listMapTools(category) : "当前任务不允许查询该地图类别。";
    }

    @Tool(description = "按返回的参数结构调用当前任务允许的具体地图工具。")
    public String callMapTool(@ToolParam(name = "name", description = "工具名称") String name,
            @ToolParam(name = "arguments", description = "工具参数对象") Map<String, Object> arguments) {
        return name != null && allowed.contains(MapTools.capability(name))
                ? delegate.callMapTool(name, arguments) : "当前任务不允许调用该地图工具。";
    }
}
