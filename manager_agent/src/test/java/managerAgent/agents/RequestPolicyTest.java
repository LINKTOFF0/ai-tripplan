package managerAgent.agents;

import io.agentscope.core.message.*;
import managerAgent.tool.*;
import org.junit.jupiter.api.Test;
import utils.MapTools;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RequestPolicyTest {
    @Test void naturalRequestsSelectNarrowCapabilities() {
        assertEquals(RequestPolicy.WEATHER, RequestPolicy.resolve(null, "珠海现在天气怎么样？"));
        assertEquals(RequestPolicy.EDIT_DATE, RequestPolicy.resolve(null, "第二天改到11月1日"));
        assertEquals(RequestPolicy.EDIT_ADVICE, RequestPolicy.resolve(null, "修改珠海渔女游玩建议"));
        assertEquals(RequestPolicy.ADD_PLACE, RequestPolicy.resolve(null, "把珠海渔女加入第二天"));
        assertEquals(RequestPolicy.EDIT_ORDER, RequestPolicy.resolve(null, "交换两个地点顺序"));
        assertEquals(RequestPolicy.ROUTE_QUERY, RequestPolicy.resolve(null, "珠海渔女到日月贝怎么去"));
        assertEquals(RequestPolicy.ROUTE_OPTIMIZATION, RequestPolicy.resolve(null, "优化当前行程路线"));
        assertEquals(RequestPolicy.PLAN, RequestPolicy.resolve(null, "珠海一日游"));
        assertEquals(RequestPolicy.PLACE_SEARCH, RequestPolicy.resolve(null, "附近有什么值得吃的"));
        assertEquals(RequestPolicy.GENERAL, RequestPolicy.resolve(null, "谢谢"));
        assertEquals(RequestPolicy.WEATHER, RequestPolicy.resolve("WEATHER", "请规划"));
        assertThrows(IllegalArgumentException.class, () -> RequestPolicy.resolve("invalid", "天气"));
    }

    @Test void weatherAndLocalEditsCannotAccessPlannerOrOtherEditTools() {
        var edits = new JourneyEditTool(JourneyEditToolTest.sample());
        try (var maps = new MapTools("", Set.of("place", "route", "weather"))) {
            var weather = ManagerAgent.requestToolkit(new RemoteAgentTool(), edits, maps, RequestPolicy.WEATHER);
            assertEquals(Set.of("listMapTools", "callMapTool"), weather.getToolNames());
            var date = ManagerAgent.requestToolkit(new RemoteAgentTool(), edits, maps, RequestPolicy.EDIT_DATE);
            assertEquals(Set.of("updateDayDate"), date.getToolNames());
            var advice = ManagerAgent.requestToolkit(new RemoteAgentTool(), edits, maps, RequestPolicy.EDIT_ADVICE);
            assertEquals(Set.of("updatePlaceAdvice"), advice.getToolNames());
            var route = ManagerAgent.requestToolkit(new RemoteAgentTool(), edits, maps, RequestPolicy.ROUTE_QUERY);
            assertFalse(route.getToolNames().contains("callRouteMakingAgent"));
            assertFalse(route.getToolNames().contains("callTripPlannerAgent"));
            var optimize = ManagerAgent.requestToolkit(new RemoteAgentTool(), edits, maps, RequestPolicy.ROUTE_OPTIMIZATION);
            assertTrue(optimize.getToolNames().contains("reorderDayPlaces"));
            assertTrue(optimize.getToolNames().contains("callRouteMakingAgent"));
            assertFalse(optimize.getToolNames().contains("callTripPlannerAgent"));
            assertTrue(ManagerAgent.requestToolkit(new RemoteAgentTool(), edits, maps, RequestPolicy.GENERAL).getToolNames().isEmpty());
        }
    }

    @Test void mapScopeRejectsWrongCategoryEvenIfModelGuessesToolName() {
        var scope = new ScopedMapTool(null, Set.of("weather"));
        assertTrue(scope.listMapTools("route").contains("不允许"));
        assertTrue(scope.callMapTool("maps_direction_walking", Map.of()).contains("不允许"));
        assertTrue(scope.callMapTool(null, Map.of()).contains("不允许"));
    }

    @Test void modelGeneratedPlanCannotUpdateWeatherOrChatCards() {
        ManagerAgent manager = new ManagerAgent((remote, edits, policy) -> prompt ->
                Msg.builder().role(MsgRole.ASSISTANT).content(List.of(TextBlock.builder().text(
                        "{\"answerMarkdown\":\"天气答复\",\"journeyPlan\":{\"id\":\"fake\",\"days\":[{\"id\":\"d1\"}]}}").build())).build());
        String events = String.join("", manager.stream("珠海天气").collectList().block(Duration.ofSeconds(5)));
        assertFalse(events.contains("JOURNEY_PLAN"));
        assertTrue(events.contains("天气答复"));
    }

    @Test void textItineraryReadsCurrentCardsWithoutOpeningEditOrPlannerTools() {
        ManagerAgent manager = new ManagerAgent((remote, edits, policy) -> prompt -> {
            assertEquals(RequestPolicy.GENERAL, policy);
            assertTrue(ManagerAgent.requestToolkit(remote, edits, null, policy).getToolNames().isEmpty());
            assertTrue(prompt.contains("\"journeyPlan\""));
            assertTrue(prompt.contains("d1p1"));
            assertTrue(prompt.contains("\"short\":\"walking\""));
            return Msg.builder().role(MsgRole.ASSISTANT).content(List.of(TextBlock.builder().text(
                    "{\"answerMarkdown\":\"第一天文字旅游规划\",\"journeyPlan\":{\"id\":\"fake\",\"days\":[{\"id\":\"d1\"}]}}").build())).build();
        });
        var request = new data.PromptSchema();
        request.setTask("GENERAL"); request.setPrompt("读取卡片给出完整文字旅游规划，不修改卡片");
        request.setJourneyPlan(JourneyEditToolTest.sample()); request.setActiveDayId("day-1");
        request.setTravelDefaults(Map.of("short", "walking", "long", "driving"));
        String events = String.join("", manager.stream(request).collectList().block(Duration.ofSeconds(5)));
        assertTrue(events.contains("第一天文字旅游规划"));
        assertFalse(events.contains("JOURNEY_PLAN"));
        assertEquals("d1p1", request.getJourneyPlan().days.get(0).places.get(0).id);
    }
}
