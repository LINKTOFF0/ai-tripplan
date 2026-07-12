package managerAgent.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import data.ResponseSchema;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.plan.PlanNotebook;
import io.agentscope.core.tool.Toolkit;
import lombok.extern.slf4j.Slf4j;
import managerAgent.hook.planHook;
import managerAgent.plan.TripPlan;
import managerAgent.tool.RemoteAgentTool;
import org.springframework.stereotype.Component;
import utils.AgentUtils;
import utils.ToolUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ManagerAgent {

    private final ReActAgent agent;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ManagerAgent() {

        //PlanNotebook
        TripPlan plan = new TripPlan();
        //Toolkit
        ToolUtils toolUtils = new ToolUtils();
        //将远程Agent封装为工具的封装注册到工具包
        Toolkit toolkit = toolUtils.getToolkit(new RemoteAgentTool());
        //计划对象
        PlanNotebook planNotebook = plan.getPlan();

        // 从 classpath 加载系统提示词
        String sysPrompt = loadPrompt("prompt.md");

        agent = AgentUtils.getReActAgentBuilder(
                        "ManagerAgent",
                        sysPrompt
                )
                .planNotebook(planNotebook)
                //拦截器
                .hook(new planHook(planNotebook))
                //工具包
                .toolkit(toolkit)
                .build();
    }

    /**
     * 从 classpath 加载 prompt 文件
     */
    private String loadPrompt(String path) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                log.warn("未找到 {}，使用默认提示词", path);
                return "你是旅行规划助手，负责理解用户需求并调用工具完成任务";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("加载 {} 失败，使用默认提示词", path, e);
            return "你是旅行规划助手，负责理解用户需求并调用工具完成任务";
        }
    }

    public ReActAgent getManagerAgent() {
        return this.agent;
    }

    public ResponseSchema run(String prompt) {
        try {
            Msg userMsg = Msg.builder()
                    .role(MsgRole.USER)
                    .content(List.of(TextBlock.builder().text(prompt).build()))
                    .build();
            Msg msg = agent.call(userMsg).block();
            String textContent = msg.getTextContent();
            ResponseSchema result = new ResponseSchema();
            result.response = textContent != null ? textContent : "Agent 未返回内容";
            return result;
        } catch (Exception e) {
            log.warn("Agent 执行异常，降级处理", e);
            ResponseSchema fallback = new ResponseSchema();
            fallback.response = "抱歉，AI 处理请求时遇到问题：" + e.getMessage();
            return fallback;
        }
    }

    /**
     * 流式执行 Agent，返回 SSE 事件流（分批延迟，防止前端卡死）。
     * 每帧为 JSON：{"type":"REASONING|TOOL_RESULT|TEXT|DONE|ERROR","text":"...","isLast":false}
     */
    public Flux<String> stream(String prompt) {
        Msg userMsg = Msg.builder()
                .role(MsgRole.USER)
                .content(List.of(TextBlock.builder().text(prompt).build()))
                .build();

        return agent.stream(userMsg)
                .map(this::eventToJson)
                .onErrorResume(e -> {
                    log.warn("Agent 流式执行异常", e);
                    Map<String, Object> err = new HashMap<>();
                    err.put("type", "ERROR");
                    err.put("text", "执行出错：" + e.getMessage());
                    return Mono.just(toJsonString(err));
                })
                .concatWith(Mono.just("{\"type\":\"DONE\",\"text\":\"\",\"isLast\":true}"));
    }

    private String eventToJson(Event event) {
        Map<String, Object> map = new HashMap<>();
        EventType type = event.getType();
        String text = event.getMessage() != null ? event.getMessage().getTextContent() : "";

        if (type == EventType.REASONING) {
            map.put("type", "REASONING");
            map.put("text", text != null ? text : "");
        } else if (type == EventType.TOOL_RESULT) {
            map.put("type", "TOOL_RESULT");
            map.put("text", text != null ? text : "");
        } else {
            map.put("type", "TEXT");
            map.put("text", text != null ? text : "");
        }
        map.put("isLast", event.isLast());

        return toJsonString(map);
    }

    private String toJsonString(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{\"type\":\"ERROR\",\"text\":\"JSON序列化失败\"}";
        }
    }
}
