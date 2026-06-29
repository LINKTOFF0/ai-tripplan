package routeMakingAgent.agents;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.a2a.server.AgentScopeA2aServer;
import io.agentscope.core.a2a.server.card.ConfigurableAgentCard;
import utils.AgentUtils;


public class RouteMakingAgent {

    public void getRouteMakingAgent() {
        //路线规划Agent Builder
        ReActAgent.Builder builder = AgentUtils.getReActAgentBuilder(
                "RouteMakingAgent",
                "路线规划Agent"
        );
        //路线规划Agent 智能体卡片
        ConfigurableAgentCard agentCard = new ConfigurableAgentCard.Builder()
                .name("RouteMakingAgent")
                .description("路线规划Agent")
                .build();

        AgentScopeA2aServer.builder(builder)
                .agentCard(agentCard)
                .build();
    }
}
