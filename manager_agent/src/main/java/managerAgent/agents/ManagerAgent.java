package managerAgent.agents;

import data.ResponseSchema;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.StructuredOutputReminder;
import io.agentscope.core.plan.PlanNotebook;
import io.agentscope.core.tool.Toolkit;
import lombok.extern.slf4j.Slf4j;
import managerAgent.hook.planHook;
import managerAgent.plan.TripPlan;
import managerAgent.tool.RemoteAgentTool;
import org.springframework.stereotype.Component;
import utils.AgentUtils;
import utils.ToolUtils;

@Slf4j
@Component
public class ManagerAgent {

    private final ReActAgent agent;

    public ManagerAgent() {

        //PlanNotebook
        TripPlan plan = new TripPlan();
        //Toolkit
        ToolUtils toolUtils = new ToolUtils();
        //将远程Agent封装为工具的封装注册到工具包
        Toolkit toolkit = toolUtils.getToolkit(new RemoteAgentTool());
        //计划对象
        PlanNotebook planNotebook = plan.getPlan();


        agent = AgentUtils.getReActAgentBuilder(
                        "ManagerAgent",
                        "负责用户需求的解决方案制定，以及任务分发"
                )
                // .enablePlan()
                .planNotebook(planNotebook)
                //拦截器
                .hook(new planHook(planNotebook))
                //工具包
                .toolkit(toolkit)
                //结构化输出：使用 TOOL_CHOICE 模式，通过 API tool_choice 参数强制模型调用 generate_response
                .structuredOutputReminder(StructuredOutputReminder.TOOL_CHOICE)
                .build();
    }
    public ReActAgent getManagerAgent() {
        return this.agent;
    }
    public ResponseSchema run(String prompt) {
        Msg msg = AgentUtils.callWithStructuredOutput(agent, prompt, ResponseSchema.class);
        try {
            return msg.getStructuredData(ResponseSchema.class);
        } catch (IllegalStateException e) {
            // 结构化输出失败时的降级处理：从消息文本中提取内容
            log.warn("Structured output not found in response, falling back to text content", e);
            String textContent = msg.getTextContent();
            ResponseSchema fallback = new ResponseSchema();
            fallback.response = textContent != null ? textContent : "Agent 未能返回结构化结果";
            return fallback;
        }
    }
}
