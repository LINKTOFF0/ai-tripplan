package tripPlannerAgent.agents;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.skill.util.JarSkillRepositoryAdapter;
import io.agentscope.core.tool.Toolkit;
import utils.AgentUtils;

import java.awt.*;
import java.io.IOException;


public class SuggestSightAgent {

    //创建景点推荐Agent
    public ReActAgent getSuggestSightAgent() {
        Toolkit toolkit = new Toolkit();
        SkillBox skillBox = new SkillBox(toolkit);

        JarSkillRepositoryAdapter repo;
        try {
            repo = new JarSkillRepositoryAdapter("skills");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        AgentSkill suggestSightsSkill = repo.getSkill("Suggest-Sights");
        skillBox.registerSkill(suggestSightsSkill);
        ReActAgent.Builder builder = AgentUtils.getReActAgentBuilder(
                        "SuggestSightsSubAgent",
                        "专注根据用户输入推荐合适景点，结合地理、偏好和评分等信息")
                .toolkit(toolkit)
                .skillBox(skillBox);

        return builder.build();
    }
}
