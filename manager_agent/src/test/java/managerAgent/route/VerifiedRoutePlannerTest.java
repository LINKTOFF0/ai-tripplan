package managerAgent.route;

import data.PromptSchema;
import managerAgent.tool.JourneyEditToolTest;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class VerifiedRoutePlannerTest {
    private PromptSchema request() {
        var request = new PromptSchema(); request.setJourneyPlan(JourneyEditToolTest.sample()); request.setActiveDayId("day-1");
        request.setTravelDefaults(Map.of("short", "walking", "long", "driving"));
        return request;
    }
    @Test void queriesBothDirectionsUsingActualPairPreferences() {
        var request = request(); request.setTravelPreferences(Map.of("d1p1:d1p2", "cycling"));
        var count = new AtomicInteger();
        var planner = new VerifiedRoutePlanner(category -> "[{\"name\":\"maps_direction_riding\"},{\"name\":\"maps_direction_driving\"},{\"name\":\"maps_direction_walking\"}]", (name, args) -> {
            int i = count.incrementAndGet();
            if (i == 1) assertEquals("maps_direction_riding", name);
            return "{\"content\":[{\"text\":\"{\\\"route\\\":{\\\"paths\\\":[{\\\"distance\\\":\\\"200\\\",\\\"duration\\\":\\\"" + (i == 1 ? 20 : 10) + "\\\"}]}}\"}]}";
        });
        var result = planner.plan(request);
        assertEquals(2, count.get());
        assertEquals(java.util.List.of("d1p2", "d1p1"), result.ids());
        assertTrue(result.evidence().contains("cycling"));
        assertTrue(result.report().contains("10 秒"));
    }
    @Test void refusesMissingMapDataAndUnconfirmedCoordinates() {
        var planner = new VerifiedRoutePlanner(category -> "[{\"name\":\"maps_direction_walking\"},{\"name\":\"maps_direction_driving\"}]",
                (name, args) -> "{\"isError\":true,\"content\":[]}");
        assertThrows(IllegalArgumentException.class, () -> planner.plan(request()));
        var pending = request(); pending.getJourneyPlan().days.get(0).places.get(0).locationStatus = "ambiguous";
        assertThrows(IllegalArgumentException.class, () -> planner.plan(pending));
        assertEquals("d1p1", pending.getJourneyPlan().days.get(0).places.get(0).id);
    }
    @Test void explainsQuotaFailureWithoutRetryingOrChangingOrder() {
        var count = new AtomicInteger();
        var planner = new VerifiedRoutePlanner(category -> "[{\"name\":\"maps_direction_driving\"},{\"name\":\"maps_direction_walking\"}]",
                (name, args) -> {
                    count.incrementAndGet();
                    return "{\"isError\":true,\"content\":[{\"text\":\"QPS_HAS_EXCEEDED_THE_LIMIT\"}]}";
                });
        var input = request();
        var error = assertThrows(IllegalArgumentException.class, () -> planner.plan(input));
        assertTrue(error.getMessage().contains("QPS_HAS_EXCEEDED_THE_LIMIT"));
        assertEquals(1, count.get());
        assertEquals("d1p1", input.getJourneyPlan().days.get(0).places.get(0).id);
    }
}
