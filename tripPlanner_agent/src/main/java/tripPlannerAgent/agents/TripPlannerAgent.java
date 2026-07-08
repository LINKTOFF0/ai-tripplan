package tripPlannerAgent.agents;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.tool.Toolkit;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import utils.AgentUtils;

import java.io.IOException;
import java.net.URISyntaxException;


@Component
public class TripPlannerAgent {

    @Bean
    public ReActAgent getTripPlannerAgent() throws URISyntaxException, IOException {

        Toolkit toolkit = new Toolkit();

        SuggestSightAgent suggestSightAgent = new SuggestSightAgent();

        TableMakerAgent tableMakerAgent = new TableMakerAgent();

        //将智能体(子Agent)作为工具
        toolkit.registration()
                .subAgent(suggestSightAgent::getSuggestSightAgent)
                .subAgent(tableMakerAgent::getTableMakerAgent)
                .apply();

        //行程规划Agent Builder
        ReActAgent.Builder builder = AgentUtils.getReActAgentBuilder(
                        "TripPlannerAgent",
                        "擅长处理景点行程规划"
                )
                //挂载工具包
                .toolkit(toolkit);
        return builder.build();
    }
}
