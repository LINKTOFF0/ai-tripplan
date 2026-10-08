package managerAgent.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import data.ResponseSchema;
import data.TripAssistantResult;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.MessageMetadataKeys;
import io.agentscope.core.model.transport.HttpTransportException;
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
import reactor.core.scheduler.Schedulers;

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
    private final RemoteAgentTool remoteAgentTool = new RemoteAgentTool();

    public ManagerAgent() {

        //PlanNotebook
        TripPlan plan = new TripPlan();
        //Toolkit
        ToolUtils toolUtils = new ToolUtils();
        //将远程Agent封装为工具的封装注册到工具包
        Toolkit toolkit = toolUtils.getToolkit(remoteAgentTool);
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
        remoteAgentTool.clearLatestJourneyPlan();
        String responsePrompt = prompt + "\n\n请用中文 Markdown 向用户汇总结果，不要强制输出 JSON。" +
                "行程卡片数据由行程规划工具单独提供，不能从 Markdown 推导。";

        return Flux.just(eventJson("REASONING", "正在理解需求并规划行程…"))
                .concatWith(Mono.fromCallable(() -> runAssistant(responsePrompt))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(this::structuredEvents))
                .onErrorResume(e -> {
                    log.warn("Agent 结构化流式执行异常", e);
                    return Flux.just(eventJson("ERROR", userFacingError(e)));
                })
                .concatWithValues("{\"type\":\"DONE\",\"text\":\"\",\"isLast\":true}");
    }

    private String userFacingError(Throwable error) {
        HttpTransportException transportError = findTransportError(error);
        if (transportError != null) {
            Integer statusCode = transportError.getStatusCode();
            if (statusCode == null) {
                return "无法连接 DeepSeek 服务，请检查管理端网络或代理配置后重试。";
            }
            return "DeepSeek 服务请求失败（HTTP " + statusCode + "），请检查模型配置后重试。";
        }
        return "执行出错：" + (error.getMessage() == null ? "未知错误" : error.getMessage());
    }

    private HttpTransportException findTransportError(Throwable error) {
        if (error == null) return null;
        if (error instanceof HttpTransportException transportError) return transportError;
        HttpTransportException fromCause = findTransportError(error.getCause());
        if (fromCause != null) return fromCause;
        for (Throwable suppressed : error.getSuppressed()) {
            HttpTransportException fromSuppressed = findTransportError(suppressed);
            if (fromSuppressed != null) return fromSuppressed;
        }
        return null;
    }

    private synchronized Msg runAssistant(String prompt) {
        return agent.call(AgentUtils.userMessage(prompt)).block();
    }

    private Flux<String> structuredEvents(Msg msg) {
        Object payload = msg.getMetadata() == null ? null : msg.getMetadata().get(MessageMetadataKeys.STRUCTURED_OUTPUT);
        TripAssistantResult result = null;
        if (payload != null) {
            result = objectMapper.convertValue(payload, TripAssistantResult.class);
        } else {
            String text = msg.getTextContent();
            if (text != null && text.stripLeading().startsWith("{")) {
                try {
                    result = objectMapper.readValue(text, TripAssistantResult.class);
                } catch (Exception ignored) {
                    log.debug("Agent returned text instead of the requested structured response");
                }
            }
            if (result == null) {
                log.warn("Agent returned no structured output; metadata keys={}, textLength={}",
                        msg.getMetadata() == null ? List.of() : msg.getMetadata().keySet(),
                        text == null ? 0 : text.length());
                if (text == null || text.isBlank()) {
                    return Flux.just(eventJson("ERROR", "AI 没有返回可显示的内容，请重试。"));
                }
                List<String> fallbackEvents = new java.util.ArrayList<>();
                fallbackEvents.add(eventJson("TEXT", text));
                data.JourneyPlanDto plannerPlan = remoteAgentTool.getLatestJourneyPlan();
                if (plannerPlan != null) {
                    normalizePlan(plannerPlan);
                    Map<String, Object> planEvent = new HashMap<>();
                    planEvent.put("type", "JOURNEY_PLAN");
                    planEvent.put("journeyPlan", plannerPlan);
                    fallbackEvents.add(toJsonString(planEvent));
                } else if (remoteAgentTool.wasTripPlannerCalled()) {
                    fallbackEvents.add(eventJson("WARNING", "这次只生成了文字答复，行程卡片未更新；现有行程已保留。"));
                }
                return Flux.fromIterable(fallbackEvents);
            }
        }
        List<String> events = new java.util.ArrayList<>();
        events.add(eventJson("TEXT", result.answerMarkdown == null ? "" : result.answerMarkdown));
        if (result.journeyPlan != null) {
            normalizePlan(result.journeyPlan);
            Map<String, Object> event = new HashMap<>();
            event.put("type", "JOURNEY_PLAN");
            event.put("journeyPlan", result.journeyPlan);
            events.add(toJsonString(event));
        }
        return Flux.fromIterable(events);
    }

    private void normalizePlan(data.JourneyPlanDto plan) {
        if (plan.id == null || plan.id.isBlank()) plan.id = "journey-" + System.currentTimeMillis();
        if (plan.days == null) plan.days = new java.util.ArrayList<>();
        for (int d = 0; d < plan.days.size(); d++) {
            data.JourneyDayDto day = plan.days.get(d);
            if (day.id == null || day.id.isBlank()) day.id = "day-" + (d + 1);
            if (day.dayNumber == null) day.dayNumber = d + 1;
            if (day.places == null) day.places = new java.util.ArrayList<>();
            for (int p = 0; p < day.places.size(); p++) {
                data.JourneyPlaceDto place = day.places.get(p);
                if (place.id == null || place.id.isBlank()) place.id = "day-" + (d + 1) + "-place-" + (p + 1);
                if (place.durationMinutes == null) place.durationMinutes = 60;
                if (place.category == null || !List.of("attraction", "food", "hotel", "transport", "other").contains(place.category)) {
                    place.category = "other";
                }
            }
        }
    }

    private String eventJson(String type, String text) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", type);
        event.put("text", text);
        return toJsonString(event);
    }

    private String toJsonString(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{\"type\":\"ERROR\",\"text\":\"JSON序列化失败\"}";
        }
    }
}
