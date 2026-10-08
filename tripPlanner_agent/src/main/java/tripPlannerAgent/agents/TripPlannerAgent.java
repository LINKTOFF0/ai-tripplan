package tripPlannerAgent.agents;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.skill.util.JarSkillRepositoryAdapter;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.ToolkitConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import utils.AgentUtils;

import java.io.IOException;


@Component
public class TripPlannerAgent {

    @Bean
    public ReActAgent getTripPlannerAgent() throws IOException {

        Toolkit toolkit = new Toolkit(ToolkitConfig.builder().parallel(false).build());
        SkillBox skillBox = new SkillBox(toolkit);
        JarSkillRepositoryAdapter repo = new JarSkillRepositoryAdapter("skills");

        // 直接加载技能作为工具，避免 subAgent 导致的 pending tool call 问题
        AgentSkill suggestSights = repo.getSkill("Suggest-Sights");
        skillBox.registerSkill(suggestSights);

        return AgentUtils.getReActAgentBuilder(
                        "TripPlannerAgent",
                        "你是行程规划专家，依据主管提供的地点、偏好和必要地图数据安排每日行程。不强制查询天气，不执行weather_check脚本；缺少实时信息时明确说明，严禁编造天气和景点信息。" +
                                " 最终必须只返回符合用户所给字段结构的有效 JSON 对象，不得返回 Markdown、代码围栏或 JSON 之外的说明。" +
                                "地点名称必须是可在地图搜索的真实地点，缺乏可靠信息时减少地点，不得猜测地址或生成经纬度。" +
                                "每张地点卡片只对应一个具体POI，name仅填写正式地点名称，午餐、晚餐、夜景等活动写入description。" +
                                "商圈一带、附近用餐、建议住宿区域不能作为地点卡片；没有已核实的餐厅或酒店时将区域建议写入当天title或已有地点description，不得编造具体店铺。每个地点city填写其实际所属城市，不要用整趟多城市行程的目的地代替。"
                )
                .toolkit(toolkit)
                .skillBox(skillBox)
                .build();
    }
}
