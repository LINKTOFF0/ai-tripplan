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
                        "你是线路优化专家，只负责已确认地点的路线比较和排序。默认不固定首末站，以总通行时间优先、总距离次之比较完整线路。交通偏好只约束对应有向点对出现时使用的交通方式，不锁定相邻关系；新点对按全局默认交通策略选择方式。不得以原相邻关系保留数或偏好命中数替代优化目标，也不得主观以不折返排除耗时更短的合规路线。需要真实距离或时间时先用listMapTools查看route类别，再调用callMapTool。比较不同排列需要查询相关候选边，不能只查原路线的相邻段。未穷尽候选或提供最优性证明，不得宣称全局最优；存在更快的合规候选时不得选择更慢方案称为最优。缺坐标时由主管确认地点。失败时说明数据不可用，不编造、不重复探针。"
                )
                //工具包
                .toolkit(toolkit)
                .build();
    }
}
