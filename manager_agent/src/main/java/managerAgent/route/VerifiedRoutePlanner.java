package managerAgent.route;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import data.JourneyPlaceDto;
import data.PromptSchema;
import utils.MapTools;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class VerifiedRoutePlanner {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Function<String, String> catalog;
    private final BiFunction<String, Map<String, Object>, String> query;
    public VerifiedRoutePlanner(MapTools tools) { this(tools::listMapTools, pacedQuery(tools)); }
    private static BiFunction<String, Map<String, Object>, String> pacedQuery(MapTools tools) {
        return (name, arguments) -> {
            // Leave room for SDK traffic sharing the same provider quota.
            try { Thread.sleep(2000); }
            catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalArgumentException("路线比较已中止，保留原顺序");
            }
            return tools.callMapTool(name, arguments);
        };
    }
    public VerifiedRoutePlanner(Function<String, String> catalog, BiFunction<String, Map<String, Object>, String> query) {
        this.catalog = catalog; this.query = query;
    }
    public record VerifiedOrder(List<String> ids, String report, String evidence) {}

    public VerifiedOrder plan(PromptSchema request) {
        if (request.getJourneyPlan() == null || request.getJourneyPlan().days == null)
            throw new IllegalArgumentException("没有当前行程");
        var day = request.getJourneyPlan().days.stream().filter(d -> d.id.equals(request.getActiveDayId()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("请选择当前日期"));
        var places = day.places;
        if (places == null || places.size() < 2 || places.size() > 8)
            throw new IllegalArgumentException("精确优化支持 2 至 8 个地点");
        if (places.stream().anyMatch(p -> !validLocation(p)))
            throw new IllegalArgumentException("请先确认所有地点的真实地图定位");
        if (places.stream().map(p -> p.id).distinct().count() != places.size())
            throw new IllegalArgumentException("地点标识重复");
        long deadline = System.nanoTime() + java.time.Duration.ofMinutes(3).toNanos();
        JsonNode tools = json(catalog.apply("route"));
        var cities = new HashMap<String, String>();
        var matrix = new RouteOrderSolver.Cost[places.size()][places.size()];
        var edges = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < places.size(); i++) for (int j = 0; j < places.size(); j++) if (i != j) {
            if (Thread.currentThread().isInterrupted() || System.nanoTime() > deadline)
                throw new IllegalArgumentException("路线比较超时，保留原顺序");
            var from = places.get(i); var to = places.get(j);
            String mode = mode(request, from, to);
            JsonNode definition = tool(tools, switch (mode) {
                case "walking" -> "walking"; case "cycling" -> "riding";
                case "transit" -> "transit"; default -> "driving";
            });
            var args = new HashMap<String, Object>();
            args.put("origin", from.longitude + "," + from.latitude);
            args.put("destination", to.longitude + "," + to.latitude);
            if (mode.equals("transit")) {
                String city1 = city(from.city, cities), city2 = city(to.city, cities);
                args.put("city", city1); args.put("cityd", city2); args.put("city1", city1); args.put("city2", city2);
            }
            JsonNode schema = definition.path("inputSchema");
            var allowed = schema.path("properties");
            if (allowed.isObject()) args.keySet().removeIf(key -> !allowed.has(key));
            JsonNode body = payload(query.apply(definition.path("name").asText(), args));
            JsonNode route = body.has("route") ? body.path("route") : body;
            JsonNode paths = route.path(mode.equals("transit") ? "transits" : "paths");
            if (!paths.isArray() || paths.isEmpty()) throw new IllegalArgumentException("候选路段没有可用数据，保留原顺序");
            RouteOrderSolver.Cost best = null;
            for (JsonNode path : paths) {
                var duration = path.has("duration") ? path.path("duration") : path.path("cost").path("duration");
                var cost = new RouteOrderSolver.Cost(number(duration), number(path.path("distance")), mode);
                if (best == null || cost.seconds() < best.seconds() || cost.seconds() == best.seconds() && cost.meters() < best.meters()) best = cost;
            }
            matrix[i][j] = best;
            edges.add(Map.of("from", from.id, "to", to.id, "mode", mode, "seconds", best.seconds(), "meters", best.meters()));
        }
        var solution = RouteOrderSolver.solve(matrix);
        var ids = solution.order().stream().map(i -> places.get(i).id).toList();
        String names = String.join(" → ", solution.order().stream().map(i -> places.get(i).name).toList());
        String report = "### 线路精确优化\n" + names + "\n\n"
                + "已查询全部 " + edges.size() + " 个有向点对，按总耗时优先、总距离次之求解；首末站均可调整。\n\n"
                + "优化前：" + solution.beforeSeconds() + " 秒 / " + solution.beforeMeters() + " 米。\n\n"
                + "优化后：" + solution.seconds() + " 秒 / " + solution.meters() + " 米。\n\n"
                + "这是本次高德路段采样及当前交通偏好下的最优开放线路，不保证未来实时交通、营业时间或游玩节奏最优。日期、地点和建议保持不变。";
        return new VerifiedOrder(ids, report, stringify(Map.of("ids", ids, "edges", edges)));
    }
    private String city(String name, Map<String, String> cache) {
        return cache.computeIfAbsent(name, key -> {
            JsonNode definition = tool(json(catalog.apply("place")), "_geo");
            JsonNode body = payload(query.apply(definition.path("name").asText(), Map.of("address", key)));
            String code = body.path("geocodes").path(0).path("citycode").asText();
            if (!code.matches("\\d{3,4}")) code = body.path("geocodes").path(0).path("adcode").asText();
            if (!code.matches("\\d{3,4}|\\d{6}")) throw new IllegalArgumentException("公交城市无法确认");
            return code;
        });
    }
    private static boolean validLocation(JourneyPlaceDto p) {
        return p != null && p.id != null && p.name != null && "matched".equals(p.locationStatus)
                && p.longitude != null && p.latitude != null && Double.isFinite(p.longitude) && Double.isFinite(p.latitude)
                && Math.abs(p.longitude) <= 180 && Math.abs(p.latitude) <= 90 && (p.longitude != 0 || p.latitude != 0);
    }
    static String mode(PromptSchema request, JourneyPlaceDto from, JourneyPlaceDto to) {
        String explicit = request.getTravelPreferences() == null ? null : request.getTravelPreferences().get(from.id + ":" + to.id);
        if (explicit != null) return validMode(explicit);
        double r = Math.PI / 180, lat = (to.latitude - from.latitude) * r, lng = (to.longitude - from.longitude) * r;
        double a = Math.pow(Math.sin(lat / 2), 2) + Math.cos(from.latitude * r) * Math.cos(to.latitude * r) * Math.pow(Math.sin(lng / 2), 2);
        String band = 6371000 * 2 * Math.asin(Math.sqrt(Math.min(1, a))) > 1000 ? "long" : "short";
        return validMode(request.getTravelDefaults() == null ? "walking" : request.getTravelDefaults().getOrDefault(band, "walking"));
    }
    private static String validMode(String mode) {
        if (!Set.of("walking", "cycling", "driving", "transit").contains(mode)) throw new IllegalArgumentException("交通方式无效");
        return mode;
    }
    private JsonNode tool(JsonNode tools, String fragment) {
        if (!tools.isArray()) throw new IllegalArgumentException("地图工具目录不可用");
        for (JsonNode item : tools) if (item.path("name").asText().contains(fragment)) return item;
        throw new IllegalArgumentException("需要的交通工具不可用");
    }
    private JsonNode json(String text) {
        try { return mapper.readTree(text); } catch (Exception e) { throw new IllegalArgumentException("地图返回数据不可用"); }
    }
    private JsonNode payload(String text) {
        JsonNode root = json(text);
        if (root.path("isError").asBoolean()) {
            String detail = root.toString();
            if (detail.contains("QPS_HAS_EXCEEDED_THE_LIMIT"))
                throw new IllegalArgumentException("高德查询频率超限，请稍后重试（QPS_HAS_EXCEEDED_THE_LIMIT）");
            if (detail.contains("INVALID_USER_KEY"))
                throw new IllegalArgumentException("高德 Web 服务密钥鉴权失败（INVALID_USER_KEY）");
            throw new IllegalArgumentException("地图查询失败，保留原顺序");
        }
        if (root.path("structuredContent").isObject()) return root.path("structuredContent");
        for (JsonNode item : root.path("content")) if (item.has("text")) return json(item.path("text").asText());
        throw new IllegalArgumentException("地图未返回可核验路线数据");
    }
    private long number(JsonNode value) {
        try { return Long.parseLong(value.asText()); } catch (Exception e) { throw new IllegalArgumentException("路线距离或耗时缺失"); }
    }
    private String stringify(Object value) {
        try { return mapper.writeValueAsString(value); } catch (Exception e) { throw new IllegalArgumentException("路线证据无法生成"); }
    }
}
