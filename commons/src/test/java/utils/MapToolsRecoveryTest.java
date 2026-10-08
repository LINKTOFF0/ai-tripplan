package utils;

import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

class MapToolsRecoveryTest {
    private static class Client extends McpClientWrapper {
        final AtomicInteger calls = new AtomicInteger();
        final AtomicInteger discoveries = new AtomicInteger();
        final AtomicInteger closes = new AtomicInteger();
        Supplier<Mono<McpSchema.CallToolResult>> response = () -> Mono.just(result("晴", false));
        boolean initFails;
        Client() { super("fake-map"); }
        public Mono<Void> initialize() {
            if (initFails) return Mono.error(new IllegalStateException("Initialization failed"));
            initialized = true;
            return Mono.empty();
        }
        public Mono<List<McpSchema.Tool>> listTools() {
            discoveries.incrementAndGet();
            var schema = new McpSchema.JsonSchema("object", Map.of("city", Map.of("type", "string")), List.of("city"), false, null, null);
            return Mono.just(List.of(McpSchema.Tool.builder().name("maps_weather").inputSchema(schema).build()));
        }
        public Mono<McpSchema.CallToolResult> callTool(String name, Map<String, Object> args) {
            calls.incrementAndGet();
            return response.get();
        }
        public void close() { initialized = false; closes.incrementAndGet(); }
    }

    private static McpSchema.CallToolResult result(String text, boolean error) {
        return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(text)), error);
    }
    private static MapTools tools(Supplier<McpClientWrapper> factory, AtomicLong clock) {
        return new MapTools(Set.of("weather"), factory, Duration.ofMillis(100), Duration.ofSeconds(10), clock::get);
    }

    @Test void connectionIsLazyAndCatalogIsCached() {
        var client = new Client(); var creations = new AtomicInteger();
        try (var tools = tools(() -> { creations.incrementAndGet(); return client; }, new AtomicLong(1))) {
            assertEquals(0, creations.get());
            assertTrue(tools.listMapTools("weather").contains("maps_weather"));
            tools.listMapTools("weather");
            assertTrue(tools.callMapTool("maps_weather", Map.of("city", "珠海")).contains("晴"));
            assertEquals(1, creations.get()); assertEquals(1, client.discoveries.get());
        }
        assertEquals(1, client.closes.get());
    }

    @Test void expiredExceptionResetsButDoesNotReplayCall() {
        var old = new Client(); var recovered = new Client(); var creations = new AtomicInteger();
        old.response = () -> Mono.error(new IllegalStateException("HTTP 404 - Invalid session id"));
        try (var tools = tools(() -> creations.getAndIncrement() == 0 ? old : recovered, new AtomicLong(1))) {
            assertTrue(tools.callMapTool("maps_weather", Map.of("city", "珠海")).contains("MCP_SESSION_EXPIRED"));
            assertEquals(1, old.calls.get()); assertEquals(1, creations.get()); assertEquals(1, old.closes.get());
            assertTrue(tools.callMapTool("maps_weather", Map.of("city", "珠海")).contains("晴"));
            assertEquals(2, creations.get()); assertEquals(1, recovered.calls.get());
        }
    }

    @Test void expiredSessionInToolResultAlsoResets() {
        var old = new Client(); var creations = new AtomicInteger();
        old.response = () -> Mono.just(result("Invalid session id", true));
        try (var tools = tools(() -> creations.getAndIncrement() == 0 ? old : new Client(), new AtomicLong(1))) {
            assertTrue(tools.callMapTool("maps_weather", Map.of("city", "珠海")).contains("MCP_SESSION_EXPIRED"));
            assertEquals(1, old.closes.get());
            assertTrue(tools.listMapTools("weather").contains("maps_weather"));
            assertEquals(2, creations.get());
        }
    }

    @Test void ordinaryToolErrorsDoNotReconnect() {
        var client = new Client(); var creations = new AtomicInteger();
        client.response = () -> Mono.just(result("Invalid city parameter", true));
        try (var tools = tools(() -> { creations.incrementAndGet(); return client; }, new AtomicLong(1))) {
            assertTrue(tools.callMapTool("maps_weather", Map.of("city", "bad")).contains("Invalid city parameter"));
            client.response = () -> Mono.error(new IllegalArgumentException("invalid argument"));
            tools.callMapTool("maps_weather", Map.of("city", "bad"));
            assertEquals(1, creations.get()); assertEquals(0, client.closes.get());
        }
    }

    @Test void missingArgumentsAndUnknownToolNeverReachServer() {
        var client = new Client();
        try (var tools = tools(() -> client, new AtomicLong(1))) {
            assertTrue(tools.callMapTool("maps_weather", Map.of()).contains("必填参数"));
            assertTrue(tools.callMapTool("unknown_weather", Map.of("city", "珠海")).contains("不存在"));
            assertEquals(0, client.calls.get());
        }
    }

    @Test void failedInitializationClosesAndBacksOffUntilNextRequestAfterCooldown() {
        var failed = new Client(); failed.initFails = true;
        var creations = new AtomicInteger(); var clock = new AtomicLong(1);
        try (var tools = tools(() -> creations.getAndIncrement() == 0 ? failed : new Client(), clock)) {
            tools.listMapTools("weather"); tools.listMapTools("weather");
            assertEquals(1, creations.get()); assertEquals(1, failed.closes.get());
            clock.addAndGet(Duration.ofSeconds(11).toNanos());
            assertTrue(tools.listMapTools("weather").contains("maps_weather"));
            assertEquals(2, creations.get());
        }
    }

    @Test void timedOutTransportIsInvalidatedWithCooldown() {
        var hung = new Client(); hung.response = Mono::never;
        var clock = new AtomicLong(1); var creations = new AtomicInteger();
        try (var tools = tools(() -> creations.getAndIncrement() == 0 ? hung : new Client(), clock)) {
            assertTrue(tools.callMapTool("maps_weather", Map.of("city", "珠海")).contains("失败"));
            assertEquals(1, hung.closes.get());
            tools.listMapTools("weather"); assertEquals(1, creations.get());
            clock.addAndGet(Duration.ofSeconds(11).toNanos());
            assertTrue(tools.callMapTool("maps_weather", Map.of("city", "珠海")).contains("晴"));
            assertEquals(2, creations.get());
        }
    }

    @Test void concurrentRequestsReconnectOnlyOnce() throws Exception {
        var old = new Client(); old.response = () -> Mono.error(new IllegalStateException("Invalid session id"));
        var recovered = new Client(); var creations = new AtomicInteger();
        try (var tools = tools(() -> creations.getAndIncrement() == 0 ? old : recovered, new AtomicLong(1))) {
            tools.listMapTools("weather");
            ExecutorService executor = Executors.newFixedThreadPool(6);
            try {
                List<Future<String>> futures = new ArrayList<>();
                for (int i = 0; i < 12; i++) futures.add(executor.submit(() -> tools.callMapTool("maps_weather", Map.of("city", "珠海"))));
                int failures = 0;
                for (var future : futures) if (future.get(5, TimeUnit.SECONDS).contains("MCP_SESSION_EXPIRED")) failures++;
                assertEquals(1, failures); assertEquals(2, creations.get());
                assertEquals(1, old.closes.get()); assertEquals(11, recovered.calls.get());
            } finally { executor.shutdownNow(); }
        }
    }

    @Test void closedServiceCannotOpenAnotherConnection() {
        var creations = new AtomicInteger();
        var tools = tools(() -> { creations.incrementAndGet(); return new Client(); }, new AtomicLong(1));
        tools.close(); tools.listMapTools("weather");
        assertEquals(0, creations.get());
    }
}
