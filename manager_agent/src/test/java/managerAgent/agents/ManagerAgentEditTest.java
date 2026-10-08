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
}
