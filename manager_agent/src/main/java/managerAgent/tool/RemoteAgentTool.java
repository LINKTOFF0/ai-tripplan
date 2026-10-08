package managerAgent.tool;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.alibaba.nacos.api.exception.NacosException;
import data.JourneyPlanDto;
import io.a2a.client.config.ClientConfig;
import io.agentscope.core.a2a.agent.A2aAgent;
import io.agentscope.core.a2a.agent.A2aAgentConfig;
import io.agentscope.core.message.Msg;
import io.agentscope.core.nacos.a2a.discovery.NacosAgentCardResolver;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import utils.NacosUtil;
import utils.PromptUtils;


@Slf4j
public class RemoteAgentTool {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile JourneyPlanDto latestJourneyPlan;
    private volatile boolean tripPlannerCalled;

    public JourneyPlanDto getLatestJourneyPlan() {
        return latestJourneyPlan;
    }

    public void clearLatestJourneyPlan() {
        latestJourneyPlan = null;
        tripPlannerCalled = false;
    }

    public boolean wasTripPlannerCalled() {
        return tripPlannerCalled;
    }

    @Tool(description = "仅用于多个已选地点的线路比较与排序优化。单段交通可直接使用公共地图工具，不需要调用此智能体。不提供未核实的铁路车次或航班。")
    public synchronized String callRouteMakingAgent(
            @ToolParam(name = "prompt",description = "已确认地点、坐标、起点约束及每段交通偏好")
            String prompt) throws NacosException {
        log.info("============");
        log.info("工具方法：路线制定智能体...正在调用中");
        log.info("============");

        if (prompt == null || prompt.isBlank()) {
            log.warn("callRouteMakingAgent 收到空 prompt，跳过调用");
            return "无法制定路线：未收到有效的起终点信息";
        }

        A2aAgent agent = createRemoteAgent("RouteMakingAgent");

        log.info("============");
        log.info("获取到的远程Agent描述："+agent.getDescription());
        log.info("============");

        log.info("============");
        log.info("这个工具方法传入的参数：" + prompt);
        log.info("============");

        PromptUtils promptUtils = new PromptUtils();
        Msg userMsg = promptUtils.getUserMsg(prompt);
        //远程Agent运行
        Msg remoteAgentResponse = agent.call(userMsg).block();
        String responseText = remoteAgentResponse == null ? null : remoteAgentResponse.getTextContent();
        return responseText == null || responseText.isBlank()
                ? "路线规划 Agent 未返回有效内容，请稍后重试。"
                : responseText;
    }

    @Tool(description = "仅在用户要求新建或整体重规划时调用，依据偏好和已确认地点输出每日行程。天气、单段路线与已有卡片局部编辑不调用此工具。")
    public synchronized String callTripPlannerAgent(
            @ToolParam(name = "prompt",description = "行程规划需求，包含目的地、天数、偏好、路线信息等")
            String prompt) throws NacosException {
        log.info("============");
        log.info("工具方法：行程规划智能体...正在调用中");
        log.info("============");

        if (prompt == null || prompt.isBlank()) {
            log.warn("callTripPlannerAgent 收到空 prompt，跳过调用");
            return "无法规划行程：未收到有效的行程需求信息";
        }
        tripPlannerCalled = true;

        A2aAgent agent = createRemoteAgent("TripPlannerAgent");

        log.info("============");
        log.info("获取到的远程Agent描述："+agent.getDescription());
        log.info("============");

        log.info("============");
        log.info("这个工具方法传入的参数：" + prompt);
        log.info("============");

        PromptUtils promptUtils = new PromptUtils();
        String structuredRequest = prompt + "\n\n完成行程规划后，必须在最终答复中额外返回一个纯 JSON 对象，且只能包含下面结构，不要代码围栏或 JSON 以外的文字。" +
                "字段：{\"id\":\"journey-id\",\"title\":\"行程标题\",\"destination\":\"城市\",\"days\":[{" +
                "\"id\":\"day-1\",\"dayNumber\":1,\"date\":\"\",\"title\":\"当天主题\",\"places\":[{" +
                "\"id\":\"place-1\",\"name\":\"真实地点名称\",\"city\":\"城市\",\"address\":\"真实或可搜索地址\",\"category\":\"attraction|food|hotel|transport|other\",\"startTime\":\"\",\"durationMinutes\":60,\"description\":\"安排说明\"}]}]}。" +
                "景点和地址必须来自已核实工具结果或可确认的真实地点；不要生成经纬度。";
        Msg userMsg = promptUtils.getUserMsg(structuredRequest);
        //远程Agent运行
        Msg remoteAgentResponse = agent.call(userMsg).block();
        String responseText = remoteAgentResponse == null ? null : remoteAgentResponse.getTextContent();
        JourneyPlanDto parsedPlan = parseJourneyPlan(responseText);
        if (parsedPlan == null) {
            log.warn("TripPlannerAgent 没有返回有效的 JourneyPlan JSON");
            return responseText == null ? "行程规划 Agent 未返回内容" : responseText;
        }
        latestJourneyPlan = parsedPlan;
        return responseText;
    }

    private JourneyPlanDto parseJourneyPlan(String responseText) {
        if (responseText == null || responseText.isBlank()) return null;
        String candidate = responseText.trim();
        if (candidate.startsWith("```") && candidate.endsWith("```")) {
            int firstLine = candidate.indexOf('\n');
            if (firstLine >= 0) candidate = candidate.substring(firstLine + 1, candidate.length() - 3).trim();
        }
        JourneyPlanDto plan = tryParseJourneyPlan(candidate);
        if (plan == null && !candidate.startsWith("{")) {
            int objectStart = candidate.indexOf('{');
            int objectEnd = candidate.lastIndexOf('}');
            if (objectStart >= 0 && objectEnd > objectStart) {
                plan = tryParseJourneyPlan(candidate.substring(objectStart, objectEnd + 1));
            }
        }
        if (plan == null) log.warn("TripPlannerAgent 返回内容未包含有效行程 JSON");
        return plan;
    }

    private A2aAgent createRemoteAgent(String name) throws NacosException {
        return A2aAgent.builder()
                .name(name)
                .agentCardResolver(new NacosAgentCardResolver(NacosUtil.getNacosClient()))
                .a2aAgentConfig(A2aAgentConfig.builder()
                        .clientConfig(ClientConfig.builder().setStreaming(false).build())
                        .build())
                .build();
    }

    private JourneyPlanDto tryParseJourneyPlan(String json) {
        try {
            JourneyPlanDto plan = objectMapper.readValue(json, JourneyPlanDto.class);
            return plan.days == null || plan.days.isEmpty() ? null : plan;
        } catch (Exception ignored) {
            return null;
        }
    }
}
