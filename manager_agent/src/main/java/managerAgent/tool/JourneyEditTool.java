package managerAgent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import data.JourneyDayDto;
import data.JourneyPlaceDto;
import data.JourneyPlanDto;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.time.LocalDate;
import java.util.*;

/** Applies validated edits to a request-owned snapshot, never to shared state. */
public final class JourneyEditTool {
    private final JourneyPlanDto plan;
    private boolean changed;

    public JourneyEditTool(JourneyPlanDto source) {
        plan = new ObjectMapper().convertValue(source, JourneyPlanDto.class);
        if (plan == null || plan.id == null || plan.days == null || plan.days.isEmpty() || plan.days.size() > 60)
            throw new IllegalArgumentException("当前行程数据无效，请刷新后重试。");
        Set<String> days = new HashSet<>();
        Set<String> places = new HashSet<>();
        for (JourneyDayDto day : plan.days) {
            if (day == null || day.id == null || !days.add(day.id) || day.places == null || day.places.size() > 100)
                throw new IllegalArgumentException("当前行程的日期或地点结构无效。");
            for (JourneyPlaceDto place : day.places) {
                if (place == null || place.id == null || !places.add(place.id))
                    throw new IllegalArgumentException("当前行程的地点标识无效。");
            }
        }
    }

    public JourneyPlanDto updatedPlan() { return changed ? plan : null; }

    private JourneyDayDto day(String id) {
        return plan.days.stream().filter(day -> day.id.equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到指定日期，请使用当前行程的dayId。"));
    }

    @Tool(description = "修改已有行程某一天的日期；成功后会自动更新用户卡片，不查询地图、不重建行程。")
    public String updateDayDate(
            @ToolParam(name = "dayId", description = "当前行程中的日期标识") String dayId,
            @ToolParam(name = "date", description = "YYYY-MM-DD日期，清空用空字符串") String date) {
        try {
            JourneyDayDto day = day(dayId);
            if (date == null || (!date.isEmpty() && (!date.matches("\\d{4}-\\d{2}-\\d{2}") || !LocalDate.parse(date).toString().equals(date))))
                return "日期无效，未修改卡片。";
            day.date = date;
            changed = true;
            return "日期已更新。";
        } catch (RuntimeException error) { return "日期修改失败，未修改卡片。"; }
    }

    @Tool(description = "修改已有地点的游玩建议，保留其他地点、顺序、坐标和用户备注。")
    public String updatePlaceAdvice(
            @ToolParam(name = "dayId", description = "日期标识") String dayId,
            @ToolParam(name = "placeId", description = "地点卡片标识") String placeId,
            @ToolParam(name = "advice", description = "游玩建议，最多1000字") String advice) {
        try {
            if (advice == null || advice.length() > 1000) return "建议内容无效，未修改卡片。";
            JourneyPlaceDto place = day(dayId).places.stream().filter(item -> item.id.equals(placeId)).findFirst().orElseThrow();
            place.advice = advice;
            changed = true;
            return "游玩建议已更新。";
        } catch (RuntimeException error) { return "未找到指定地点，未修改卡片。"; }
    }

    @Tool(description = "按指定地点ID顺序重排某一天；必须包含该天所有原有地点且每个出现一次。其他天和地点资料不变。")
    public String reorderDayPlaces(
            @ToolParam(name = "dayId", description = "日期标识") String dayId,
            @ToolParam(name = "placeIds", description = "该天全部地点ID，按新顺序排列") List<String> placeIds) {
        try {
            JourneyDayDto day = day(dayId);
            Map<String, JourneyPlaceDto> existing = new HashMap<>();
            day.places.forEach(place -> existing.put(place.id, place));
            if (placeIds == null || placeIds.size() != existing.size()
                    || new HashSet<>(placeIds).size() != existing.size() || !existing.keySet().containsAll(placeIds))
                return "顺序无效，必须保留该天全部地点，未修改卡片。";
            day.places = new ArrayList<>(placeIds.stream().map(existing::get).toList());
            changed = true;
            return "地点顺序已更新。";
        } catch (RuntimeException error) { return "未找到指定日期，未修改卡片。"; }
    }

    @Tool(description = "向已有日期添加用户明确指定或确认的具体POI。不得把商圈一带、附近用餐或住宿区域当作地点。坐标由地图另行确认，不伪造坐标。")
    public String addJourneyPlace(
            @ToolParam(name = "dayId", description = "日期标识") String dayId,
            @ToolParam(name = "name", description = "用户已指定或确认的具体地点正式名称") String name,
            @ToolParam(name = "city", description = "地点所属城市") String city,
            @ToolParam(name = "address", description = "已知地址，未知留空") String address,
            @ToolParam(name = "category", description = "attraction, food, hotel, transport or other") String category) {
        try {
            JourneyDayDto day = day(dayId);
            if (name == null || name.isBlank() || name.length() > 100 || city == null || city.isBlank()
                    || city.length() > 100 || address == null || address.length() > 300
                    || !Set.of("attraction", "food", "hotel", "transport", "other").contains(category)
                    || name.matches(".*(一带|附近用餐|住宿区域|建议住宿).*")) return "地点资料无效，未添加卡片。";
            if (day.places.size() >= 100) return "当天地点数量已达上限，未添加卡片。";
            if (day.places.stream().anyMatch(place -> name.trim().equals(place.name) && city.trim().equals(place.city)))
                return "该地点已在当天行程中，未重复添加。";
            JourneyPlaceDto place = new JourneyPlaceDto();
            place.id = "place-" + UUID.randomUUID();
            place.name = name.trim(); place.city = city.trim(); place.address = address.trim(); place.category = category;
            place.startTime = ""; place.durationMinutes = 60; place.description = "";
            place.longitude = 0d; place.latitude = 0d; place.locationStatus = "pending";
            place.icon = category.equals("food") ? "utensils" : category.equals("hotel") ? "store" : "landmark";
            day.places.add(place);
            changed = true;
            return "地点卡片已添加，地图定位待确认；不会生成虚构坐标。";
        } catch (RuntimeException error) { return "地点添加失败，未添加卡片。"; }
    }
}
