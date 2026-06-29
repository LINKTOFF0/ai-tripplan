package tripPlannerAgent.agents;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.a2a.server.AgentScopeA2aServer;
import io.agentscope.core.a2a.server.card.ConfigurableAgentCard;
import org.springframework.beans.factory.annotation.Configurable;
import utils.AgentUtils;

public class TripPlannerAgent {

    public void getTripPlannerAgent() {
        //行程规划Agent Builder
        ReActAgent.Builder builder = AgentUtils.getReActAgentBuilder(
                "TripPlannerAgent",
                "行程规划Agent"
        );
        //行程规划Agent 智能体卡片
        ConfigurableAgentCard agentCard = new ConfigurableAgentCard.Builder()
                .name("TripPlannerAgent")
                .description("行程规划Agent")
                .build();

        AgentScopeA2aServer.builder(builder)
                .agentCard(agentCard)
                .build();
    }
}
