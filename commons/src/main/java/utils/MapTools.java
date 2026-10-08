package utils;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.spec.McpSchema;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.LongSupplier;

/** Lazily connects to MCP and limits the capabilities available to an agent. */
public final class MapTools implements AutoCloseable {
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private final Set<String> capabilities;
    private final Supplier<McpClientWrapper> factory;
    private final Duration timeout;
    private final Duration cooldown;
    private final LongSupplier clock;
    private final ObjectMapper mapper = new ObjectMapper();
    private McpClientWrapper client;
    private List<McpSchema.Tool> catalog = List.of();
    private long unavailableUntil;
    private boolean closed;

    public MapTools(String address, Set<String> capabilities) {
        this(capabilities, () -> {
            String apiKey = System.getenv("AMAP_MAPS_API_KEY");
            if (apiKey == null || apiKey.isBlank()) apiKey = System.getProperty("AMAP_MAPS_API_KEY");
            if (apiKey != null && !apiKey.isBlank()) {
                return McpClientBuilder.create("GaodeMapMCPServer")
                        .streamableHttpTransport("https://mcp.amap.com/mcp")
                        .queryParam("key", apiKey.trim())
                        .timeout(TIMEOUT).buildAsync().block(TIMEOUT);
            }
            if (address == null || address.isBlank()) throw new IllegalStateException("Map MCP is not configured");
            return McpClientBuilder.create("GaodeMapMCPServer").sseTransport(address)
                    .timeout(TIMEOUT).buildAsync().block(TIMEOUT);
        }, TIMEOUT, Duration.ofSeconds(10), System::nanoTime);
    }

    MapTools(Set<String> capabilities, Supplier<McpClientWrapper> factory, Duration timeout,
             Duration cooldown, LongSupplier clock) {
        this.capabilities = Set.copyOf(capabilities); this.factory = factory;
        this.timeout = timeout; this.cooldown = cooldown; this.clock = clock;
    }

    public static String capability(String name) {
        if (name == null) return "unsupported";
        String value = name.toLowerCase(java.util.Locale.ROOT);
        if (value.contains("weather")) return "weather";
        if (value.contains("direction") || value.contains("distance")) return "route";
        if (value.contains("text_search") || value.contains("around_search")
                || value.contains("search_detail") || value.contains("geo")) return "place";
        return "unsupported";
    }

    private synchronized McpClientWrapper client() {
        if (closed) throw new IllegalStateException("Map MCP is closed");
        if (unavailableUntil != 0 && clock.getAsLong() < unavailableUntil)
            throw new IllegalStateException("Map MCP is cooling down");
        if (client != null) return client;
        McpClientWrapper pending = null;
        try {
            pending = factory.get();
            if (pending == null) throw new IllegalStateException("Map MCP connection failed");
            pending.initialize().block(timeout);
            List<McpSchema.Tool> discovered = pending.listTools().block(timeout);
            if (discovered == null) throw new IllegalStateException("Map MCP catalog unavailable");
            catalog = List.copyOf(discovered);
            client = pending;
            unavailableUntil = 0;
            return client;
        } catch (RuntimeException error) {
            safeClose(pending);
            unavailableUntil = clock.getAsLong() + cooldown.toNanos();
            throw error;
        }
    }

    @Tool(description = "按需查看地图工具及参数结构。category为place（地点/坐标）、route（路线/距离）或weather（天气）。")
    public synchronized String listMapTools(@ToolParam(name = "category", description = "place, route or weather") String category) {
        if (category == null || !capabilities.contains(category)) return "该地图类别不可用。";
        try {
            client();
            return mapper.writeValueAsString(catalog.stream().filter(tool -> category.equals(capability(tool.name()))).toList());
        } catch (Exception error) {
            return "地图服务暂不可用；保留已有行程，不编造数据，不反复重试。";
        }
    }

    @Tool(description = "调用listMapTools列出的具体地图工具，arguments须符合返回的参数结构，只查询当前任务需要的数据。")
    public synchronized String callMapTool(
            @ToolParam(name = "name", description = "地图工具名称") String name,
            @ToolParam(name = "arguments", description = "符合工具参数结构的对象") Map<String, Object> arguments) {
        if (name == null || !capabilities.contains(capability(name))) return "不允许调用该地图工具。";
        try {
            McpClientWrapper current = client();
            var definition = catalog.stream().filter(tool -> tool.name().equals(name)).findFirst().orElse(null);
            if (definition == null) return "工具不存在，请使用已列出的工具。";
            Map<String, Object> args = arguments == null ? Map.of() : arguments;
            if (definition.inputSchema() != null && definition.inputSchema().required() != null
                    && definition.inputSchema().required().stream().anyMatch(key -> !args.containsKey(key) || args.get(key) == null))
                return "缺少地图工具的必填参数，未执行查询。";
            var result = current.callTool(name, args).block(timeout);
            if (result == null) return "地图服务返回空结果，不得编造数据。";
            if (Boolean.TRUE.equals(result.isError()) && expiredSession(result.content().toString())) {
                invalidate(false);
                return "MCP_SESSION_EXPIRED：地图会话已失效，旧连接已清理。下次请求重新连接，本次不重试。";
            }
            return mapper.writeValueAsString(result);
        } catch (Exception error) {
            if (expiredSession(error)) {
                invalidate(false);
                return "MCP_SESSION_EXPIRED：地图会话已失效，旧连接已清理。下次请求重新连接，本次不重试。";
            }
            if (transportFailure(error)) invalidate(true);
            return "地图查询失败，该数据不可用；不要编造结果或反复重试。";
        }
    }

    private static boolean expiredSession(String message) {
        if (message == null) return false;
        String text = message.toLowerCase(java.util.Locale.ROOT);
        return text.contains("invalid session id") || text.contains("session expired")
                || text.contains("session not found") || text.contains("session has expired");
    }

    private static boolean expiredSession(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (expiredSession(current.getMessage())) return true;
        }
        return false;
    }

    private static boolean transportFailure(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof java.io.IOException || current instanceof java.util.concurrent.TimeoutException)
                return true;
        }
        return false;
    }

    private void invalidate(boolean backoff) {
        McpClientWrapper old = client;
        client = null; catalog = List.of();
        if (backoff) unavailableUntil = clock.getAsLong() + cooldown.toNanos();
        safeClose(old);
    }

    private static void safeClose(McpClientWrapper target) {
        if (target != null) {
            try { target.close(); } catch (RuntimeException ignored) { /* Do not mask the original failure. */ }
        }
    }

    public synchronized void close() {
        closed = true;
        invalidate(false);
    }
}
