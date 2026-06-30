package tripPlannerAgent.agents;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.a2a.server.AgentScopeA2aServer;
import io.agentscope.core.a2a.server.card.ConfigurableAgentCard;
import io.agentscope.core.a2a.server.transport.DeploymentProperties;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import utils.AgentUtils;
@Component
public class TripPlannerAgent {

    @Bean
    public ReActAgent getTripPlannerAgent() {
        //行程规划Agent Builder
        ReActAgent.Builder builder = AgentUtils.getReActAgentBuilder(
                "TripPlannerAgent",
                "行程规划Agent"
        );
        return builder.build();
        /*
        //======手动写入主动写入注册中心======
        //手动写入的情况下需要AgentScopeA2aServer启动
        //行程规划Agent 智能体卡片
        ConfigurableAgentCard agentCard = new ConfigurableAgentCard.Builder()
                .name("TripPlannerAgent")
                .description("行程规划Agent")
                .build();
        //将智能体卡片写入到AgentScope自带的注册中心
        AgentScopeA2aServer.builder(builder)
                .agentCard(agentCard)
                .deploymentProperties(new DeploymentProperties("localhost", 8080))
                .build();
                */
    }
}
