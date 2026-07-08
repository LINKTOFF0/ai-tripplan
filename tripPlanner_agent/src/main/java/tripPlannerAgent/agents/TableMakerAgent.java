package tripPlannerAgent.agents;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.skill.util.JarSkillRepositoryAdapter;
import io.agentscope.core.tool.Toolkit;
import utils.AgentUtils;

import java.io.IOException;

public class TableMakerAgent {


    public ReActAgent getTableMakerAgent() {
        Toolkit toolkit = new Toolkit();
        SkillBox skillBox = new SkillBox(toolkit);

        JarSkillRepositoryAdapter repo;
        try {
            repo = new JarSkillRepositoryAdapter("skills");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        AgentSkill makeTableSkill = repo.getSkill("Make-A-Table");
        skillBox.registerSkill(makeTableSkill);

        // 如果表格制作需要独立工具，可以在这里注册
        // skillBox.registration().tool(...);

        ReActAgent.Builder builder = AgentUtils.getReActAgentBuilder(
                        "MakeTableSubAgent",
                        "擅长将结构化数据（如景点列表）整理为清晰表格")
                .toolkit(toolkit)
                .skillBox(skillBox);

        return builder.build();
    }
}
