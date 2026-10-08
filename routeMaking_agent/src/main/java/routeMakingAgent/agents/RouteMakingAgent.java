package routeMakingAgent.agents;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.tool.Toolkit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import utils.MapTools;
import utils.AgentUtils;
import utils.ToolUtils;

import java.util.Set;

@Component
@Slf4j
public class RouteMakingAgent {

    @Value("${mcp.amap_addr:#{null}}")
    private String amapAddr;

    @Bean(destroyMethod = "close")
    public MapTools routeMapTools() {
        return new MapTools(amapAddr, Set.of("route"));
    }

    @Bean
    public ReActAgent getRouteMakingAgent(MapTools routeMapTools) {



        //Toolkit
        ToolUtils toolUtils = new ToolUtils();
        Toolkit toolkit = toolUtils.getToolkit(routeMapTools);

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
                        "你是线路优化专家，只负责已确认地点的路线比较和排序。需要真实距离或时间时先用listMapTools查看route类别，再调用callMapTool。只查询受影响路段，尊重每段交通偏好。缺坐标时由主管确认地点。失败时说明数据不可用，不编造、不重复探针。"
                )
                //工具包
                .toolkit(toolkit)
                .build();
    }
}
