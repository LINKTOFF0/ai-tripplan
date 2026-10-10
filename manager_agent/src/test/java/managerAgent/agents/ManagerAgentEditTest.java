package managerAgent.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import data.PromptSchema;
import io.agentscope.core.message.*;
import io.agentscope.core.tool.ToolCallParam;
import managerAgent.tool.JourneyEditToolTest;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ManagerAgentEditTest {
    @Test void verifiedOrderCannotBeOverriddenByModel() {
        ManagerAgent manager = new ManagerAgent((remote, edits, policy) -> prompt -> {
            assertTrue(edits.reorderDayPlaces("day-1", List.of("d1p1", "d1p2")).contains("不一致"));
            return Msg.builder().role(MsgRole.ASSISTANT).content(List.of(TextBlock.builder().text("模型建议原顺序").build())).build();
        }, request -> new managerAgent.route.VerifiedRoutePlanner.VerifiedOrder(List.of("d1p2", "d1p1"), "已核验报告", "{}"));
        PromptSchema request = new PromptSchema(); request.setPrompt("优化路线"); request.setTask("ROUTE_OPTIMIZATION");
        request.setJourneyPlan(JourneyEditToolTest.sample()); request.setActiveDayId("day-1");
        String events = String.join("", manager.stream(request).collectList().block(Duration.ofSeconds(5)));
        assertTrue(events.contains("JOURNEY_PLAN")); assertTrue(events.contains("已核验报告"));
        assertFalse(events.contains("模型建议原顺序"));
        assertEquals("d1p1", request.getJourneyPlan().days.get(0).places.get(0).id);
    }
    @Test void failedMatrixDoesNotCallModelOrEditCards() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        ManagerAgent manager = new ManagerAgent((remote, edits, policy) -> prompt -> { calls.incrementAndGet(); throw new AssertionError(); },
                request -> { throw new IllegalArgumentException("地图不可用"); });
        PromptSchema request = new PromptSchema(); request.setPrompt("优化路线"); request.setTask("ROUTE_OPTIMIZATION");
        request.setJourneyPlan(JourneyEditToolTest.sample()); request.setActiveDayId("day-1");
        String events = String.join("", manager.stream(request).collectList().block(Duration.ofSeconds(5)));
        assertEquals(0, calls.get()); assertFalse(events.contains("JOURNEY_PLAN")); assertTrue(events.contains("顺序已保留"));
    }
    @Test void routeRequestCarriesPreferencesAndReturnsOnlyStructuredPermutation() throws Exception {
        ManagerAgent manager = new ManagerAgent((remote, edits, policy) -> prompt -> {
            assertEquals(RequestPolicy.ROUTE_OPTIMIZATION, policy);
            assertTrue(prompt.contains("\"travelPreferences\":{\"d1p1:d1p2\":\"cycling\"}"));
            assertTrue(prompt.contains("\"long\":\"transit\""));
            assertTrue(prompt.contains("不固定首末站"));
            assertTrue(prompt.contains("不锁定相邻关系"));
            assertTrue(prompt.contains("不得以偏好命中数量"));
            assertTrue(edits.reorderDayPlaces("day-1", List.of("d1p1", "d1p3", "d1p2")).contains("已更新"));
            return Msg.builder().role(MsgRole.ASSISTANT).content(List.of(TextBlock.builder().text("已按卡片优化").build())).build();
        });
        PromptSchema request = new PromptSchema();
        request.setPrompt("优化当前路线"); request.setTask("ROUTE_OPTIMIZATION");
        request.setJourneyPlan(JourneyEditToolTest.sample()); request.setActiveDayId("day-1");
        var extra = new ObjectMapper().convertValue(request.getJourneyPlan().days.get(0).places.get(1), data.JourneyPlaceDto.class);
        extra.id = "d1p3";
        request.getJourneyPlan().days.get(0).places.add(extra);
        request.setTravelPreferences(Map.of("d1p1:d1p2", "cycling", "invalid", "unknown"));
        request.setTravelDefaults(Map.of("short", "walking", "long", "transit"));
        var mapper = new ObjectMapper();
        var event = manager.stream(request).collectList().block(Duration.ofSeconds(5)).stream()
                .map(text -> { try { return mapper.readTree(text); } catch (Exception e) { throw new AssertionError(e); } })
                .filter(node -> node.path("type").asText().equals("JOURNEY_PLAN")).findFirst().orElseThrow();
        assertEquals("ROUTE_OPTIMIZATION", event.path("task").asText());
        assertEquals("edit", event.path("mode").asText());
        assertEquals("d1p3", event.path("journeyPlan").path("days").get(0).path("places").get(1).path("id").asText());
        assertEquals("d1p1", request.getJourneyPlan().days.get(0).places.get(0).id);
    }

    @Test void actualEditProducesStructuredEventWithPreservedCards() throws Exception {
        ManagerAgent manager = new ManagerAgent((remote, edits, policy) -> prompt -> {
            assertTrue(prompt.contains("day-2"));
            var toolkit = ManagerAgent.requestToolkit(remote, edits, null, policy);
            var arguments = Map.<String, Object>of("dayId", "day-2", "date", "2026-11-01");
            var invocation = ToolCallParam.builder().toolUseBlock(ToolUseBlock.builder()
                    .id("edit-date").name("updateDayDate").input(arguments)
                    .content("{\"dayId\":\"day-2\",\"date\":\"2026-11-01\"}").build()).input(arguments).build();
            var toolResult = toolkit.callTool(invocation).block(Duration.ofSeconds(5));
            String toolText = ((TextBlock) toolResult.getOutput().get(0)).getText();
            assertTrue(toolText.contains("已更新"), toolText);
            return Msg.builder().role(MsgRole.ASSISTANT).content(List.of(TextBlock.builder().text("日期已修改").build())).build();
        });
        PromptSchema request = new PromptSchema();
        request.setPrompt("第二天改到11月1日"); request.setJourneyPlan(JourneyEditToolTest.sample()); request.setActiveDayId("day-2");
        var events = manager.stream(request).collectList().block(Duration.ofSeconds(5));
        var mapper = new ObjectMapper();
        var event = events.stream().map(text -> { try { return mapper.readTree(text); } catch (Exception e) { throw new AssertionError(e); } })
                .filter(node -> node.path("type").asText().equals("JOURNEY_PLAN")).findFirst().orElseThrow();
        assertEquals("edit", event.path("mode").asText());
        assertEquals("2026-11-01", event.path("journeyPlan").path("days").get(1).path("date").asText());
        assertEquals(113.59, event.path("journeyPlan").path("days").get(0).path("places").get(0).path("longitude").asDouble());
        assertEquals("2026-10-08", request.getJourneyPlan().days.get(1).date);
    }

    @Test void failedEditDoesNotEmitReplacementPlan() {
        ManagerAgent manager = new ManagerAgent((remote, edits, policy) -> prompt -> {
            edits.updateDayDate("day-2", "2026-02-30");
            return Msg.builder().role(MsgRole.ASSISTANT).content(List.of(TextBlock.builder().text("日期无效，未修改").build())).build();
        });
        PromptSchema request = new PromptSchema();
        request.setPrompt("修改日期"); request.setJourneyPlan(JourneyEditToolTest.sample());
        assertFalse(String.join("", manager.stream(request).collectList().block(Duration.ofSeconds(5))).contains("JOURNEY_PLAN"));
    }

    @Test void routeFailureOrInvalidPermutationLeavesCardsUntouched() {
        ManagerAgent manager = new ManagerAgent((remote, edits, policy) -> prompt -> {
            assertTrue(edits.reorderDayPlaces("day-1", List.of("d1p1", "unknown")).contains("无效"));
            return Msg.builder().role(MsgRole.ASSISTANT).content(List.of(TextBlock.builder()
                    .text("地图工具不可用，现有顺序已保留").build())).build();
        });
        PromptSchema request = new PromptSchema();
        request.setPrompt("优化路线"); request.setTask("ROUTE_OPTIMIZATION");
        request.setJourneyPlan(JourneyEditToolTest.sample()); request.setActiveDayId("day-1");
        String events = String.join("", manager.stream(request).collectList().block(Duration.ofSeconds(5)));
        assertFalse(events.contains("JOURNEY_PLAN"));
        assertTrue(events.contains("WARNING"));
        assertEquals("d1p1", request.getJourneyPlan().days.get(0).places.get(0).id);
    }
}
