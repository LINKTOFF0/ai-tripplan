package tripPlannerAgent.agents;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.skill.util.JarSkillRepositoryAdapter;
import io.agentscope.core.tool.Toolkit;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import utils.AgentUtils;

import java.io.IOException;


@Component
public class TripPlannerAgent {

    @Bean
    public ReActAgent getTripPlannerAgent() throws IOException {

        Toolkit toolkit = new Toolkit();
        SkillBox skillBox = new SkillBox(toolkit);
        JarSkillRepositoryAdapter repo = new JarSkillRepositoryAdapter("skills");

        // 直接加载技能作为工具，避免 subAgent 导致的 pending tool call 问题
        AgentSkill suggestSights = repo.getSkill("Suggest-Sights");
        skillBox.registerSkill(suggestSights);

        AgentSkill makeTable = repo.getSkill("Make-A-Table");
        skillBox.registerSkill(makeTable);

        return AgentUtils.getReActAgentBuilder(
                        "TripPlannerAgent",
                        "你是行程规划专家。规划前必须先用 weather_check 脚本查目的地天气，所有数据整理后用 recalc 脚本生成表格。严禁编造天气和景点信息。"
                )
                .toolkit(toolkit)
                .skillBox(skillBox)
                .build();
    }
}
