package managerAgent.tool;

import data.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class JourneyEditToolTest {
    @Test void routeOptimizationAllowsNewFirstPlaceButCannotChangeOtherDay() {
        var tools = new JourneyEditTool(sample(), "day-1");
        assertTrue(tools.reorderDayPlaces("day-2", List.of("d2p1", "d2p2")).contains("当前日期"));
        assertNull(tools.updatedPlan());
        assertTrue(tools.reorderDayPlaces("day-1", List.of("d1p2", "d1p1")).contains("已更新"));
        assertEquals("d1p2", tools.updatedPlan().days.get(0).places.get(0).id);
        assertThrows(IllegalArgumentException.class, () -> new JourneyEditTool(sample(), "missing"));
    }
    public static JourneyPlanDto sample() {
        JourneyPlanDto plan = new JourneyPlanDto();
        plan.id = "journey"; plan.destination = "珠海";
        for (int index = 1; index <= 2; index++) {
            JourneyDayDto day = new JourneyDayDto();
            day.id = "day-" + index; day.dayNumber = index; day.date = "2026-10-08"; day.title = "原主题";
            for (int p = 1; p <= 2; p++) {
                JourneyPlaceDto place = new JourneyPlaceDto();
                place.id = "d" + index + "p" + p; place.name = "景点" + p; place.city = "珠海";
                place.note = "用户备注"; place.advice = "原建议"; place.category = "attraction";
                place.longitude = 113.59; place.latitude = 22.25; place.locationStatus = "matched";
                day.places.add(place);
            }
            plan.days.add(day);
        }
        return plan;
    }

    @Test void dateEditIsIsolatedAndPreservesOtherDaysAndPlaceData() {
        var source = sample();
        var tools = new JourneyEditTool(source);
        assertNull(tools.updatedPlan());
        assertEquals("日期已更新。", tools.updateDayDate("day-2", "2026-11-01"));
        var result = tools.updatedPlan();
        assertEquals("2026-11-01", result.days.get(1).date);
        assertEquals("2026-10-08", source.days.get(1).date);
        assertEquals("2026-10-08", result.days.get(0).date);
        assertEquals("用户备注", result.days.get(1).places.get(0).note);
        assertEquals(113.59, result.days.get(1).places.get(0).longitude);
    }

    @Test void invalidDatesAndUnknownDayCannotMutate() {
        var tools = new JourneyEditTool(sample());
        for (String date : List.of("2026-02-30", "2026-2-01", "明天"))
            assertTrue(tools.updateDayDate("day-1", date).contains("未修改"));
        assertTrue(tools.updateDayDate("unknown", "2026-11-01").contains("未修改"));
        assertNull(tools.updatedPlan());
    }

    @Test void adviceOnlyChangesTargetPlace() {
        var tools = new JourneyEditTool(sample());
        assertTrue(tools.updatePlaceAdvice("day-1", "d1p1", "看日落").contains("已更新"));
        assertEquals("看日落", tools.updatedPlan().days.get(0).places.get(0).advice);
        assertEquals("原建议", tools.updatedPlan().days.get(0).places.get(1).advice);
        assertEquals("用户备注", tools.updatedPlan().days.get(0).places.get(0).note);
    }

    @Test void reorderingRequiresExactPermutationAndRetainsFields() {
        var tools = new JourneyEditTool(sample());
        assertTrue(tools.reorderDayPlaces("day-1", List.of("d1p1", "d1p1")).contains("无效"));
        assertTrue(tools.reorderDayPlaces("day-1", List.of("d1p1", "unknown")).contains("无效"));
        assertTrue(tools.reorderDayPlaces("day-1", List.of("d1p2")).contains("无效"));
        assertNull(tools.updatedPlan());
        assertTrue(tools.reorderDayPlaces("day-1", List.of("d1p2", "d1p1")).contains("已更新"));
        assertEquals("d1p2", tools.updatedPlan().days.get(0).places.get(0).id);
        assertEquals("matched", tools.updatedPlan().days.get(0).places.get(0).locationStatus);
        assertEquals("d2p1", tools.updatedPlan().days.get(1).places.get(0).id);
    }

    @Test void addingPlaceDoesNotInventCoordinatesOrRemoveExistingPlaces() {
        var tools = new JourneyEditTool(sample());
        assertTrue(tools.addJourneyPlace("day-1", "吉大商圈一带", "珠海", "", "food").contains("无效"));
        assertNull(tools.updatedPlan());
        assertTrue(tools.addJourneyPlace("day-1", "珠海渔女", "珠海", "情侣中路", "attraction").contains("已添加"));
        assertEquals(3, tools.updatedPlan().days.get(0).places.size());
        var added = tools.updatedPlan().days.get(0).places.get(2);
        assertEquals("pending", added.locationStatus);
        assertEquals(0d, added.longitude);
        assertTrue(tools.addJourneyPlace("day-1", "珠海渔女", "珠海", "情侣中路", "attraction").contains("未重复"));
        assertEquals(3, tools.updatedPlan().days.get(0).places.size());
    }

    @Test void invalidSnapshotIsRejected() {
        var plan = sample();
        plan.days.get(1).places.get(0).id = "d1p1";
        assertThrows(IllegalArgumentException.class, () -> new JourneyEditTool(plan));
    }
}
