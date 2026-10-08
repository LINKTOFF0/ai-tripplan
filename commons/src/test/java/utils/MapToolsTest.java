package utils;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class MapToolsTest {
    @Test void classifiesSupportedCapabilities() {
        assertEquals("weather", MapTools.capability("maps_weather"));
        assertEquals("route", MapTools.capability("maps_direction_walking"));
        assertEquals("route", MapTools.capability("maps_distance"));
        assertEquals("place", MapTools.capability("maps_text_search"));
        assertEquals("place", MapTools.capability("maps_geo"));
        assertEquals("unsupported", MapTools.capability("maps_ip_location"));
    }

    @Test void routeOnlyToolsRejectOtherCapabilitiesWithoutConnecting() {
        try (var tools = new MapTools("", Set.of("route"))) {
            assertEquals("该地图类别不可用。", tools.listMapTools("weather"));
            assertEquals("不允许调用该地图工具。", tools.callMapTool("maps_weather", Map.of()));
            assertEquals("不允许调用该地图工具。", tools.callMapTool("maps_text_search", Map.of()));
            assertEquals("不允许调用该地图工具。", tools.callMapTool(null, Map.of()));
        }
    }

    @Test void unavailableServiceReturnsControlledFailure() {
        try (var tools = new MapTools("", Set.of("route"))) {
            assertTrue(tools.listMapTools("route").contains("暂不可用"));
            assertTrue(tools.callMapTool("maps_distance", Map.of()).contains("查询失败"));
        }
    }
}
