package tripPlannerAgent.tool;

import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.skill.util.JarSkillRepositoryAdapter;
import io.agentscope.core.tool.Toolkit;

import java.io.IOException;

public class SkillBoxFactory {
    public static SkillBox buildSuggestSightSkillBox() {
        Toolkit toolkit = new Toolkit();
        //构建Skill，并将工具包和Skill结合
        SkillBox skillBox = new SkillBox(toolkit);

        try (JarSkillRepositoryAdapter repo =
                     new JarSkillRepositoryAdapter("skills")) {
            AgentSkill suggestSightsSkill =
                    repo.getSkill("Suggest-Sights");
            skillBox.registerSkill(suggestSightsSkill);
        } catch (IOException e) {
            throw new RuntimeException("加载 Suggest-Sights Skill 失败", e);
        }
        return skillBox;
    }
    public static SkillBox buildTableMakerSkillBox() {
        Toolkit toolkit = new Toolkit();
        SkillBox skillBox = new SkillBox(toolkit);

        try (JarSkillRepositoryAdapter repo =
                     new JarSkillRepositoryAdapter("skills")) {
            AgentSkill makeTableSkill =
                    repo.getSkill("Make-A-Table");
            skillBox.registerSkill(makeTableSkill);

        } catch (IOException e) {
            throw new RuntimeException("加载 Make-A-Table Skill 失败", e);
        }
        return skillBox;
    }
}
