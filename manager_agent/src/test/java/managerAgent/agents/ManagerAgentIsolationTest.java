package managerAgent.agents;

import data.JourneyDayDto;
import data.JourneyPlanDto;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import managerAgent.tool.RemoteAgentTool;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ManagerAgentIsolationTest {
    private static Msg answer(String text) {
        return Msg.builder().role(MsgRole.ASSISTANT).content(List.of(TextBlock.builder().text(text).build())).build();
    }

    private static void setPlan(RemoteAgentTool tool, String city) {
        try {
            JourneyPlanDto plan = new JourneyPlanDto();
            plan.destination = city;
            plan.days.add(new JourneyDayDto());
            var field = RemoteAgentTool.class.getDeclaredField("latestJourneyPlan");
            field.setAccessible(true);
            field.set(tool, plan);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    @Test void concurrentStreamsAndSyncRequestHaveIndependentToolsAndMemory() throws Exception {
        Set<RemoteAgentTool> instances = ConcurrentHashMap.newKeySet();
        CountDownLatch entered = new CountDownLatch(3);
        ManagerAgent manager = new ManagerAgent((tool, edits, policy) -> {
            assertTrue(instances.add(tool));
            AtomicInteger calls = new AtomicInteger();
            return prompt -> {
                assertEquals(1, calls.incrementAndGet());
                String city = prompt.split("\\n")[0];
                setPlan(tool, city);
                entered.countDown();
                try { assertTrue(entered.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
                return answer(city);
            };
        });
        try (var executor = Executors.newFixedThreadPool(3)) {
            var zhuhai = executor.submit(() -> manager.stream("珠海一日游").collectList().block(Duration.ofSeconds(10)));
            var shunde = executor.submit(() -> manager.stream("顺德一日游").collectList().block(Duration.ofSeconds(10)));
            var weather = executor.submit(() -> manager.run("天气"));
            String first = String.join("", zhuhai.get(10, TimeUnit.SECONDS));
            String second = String.join("", shunde.get(10, TimeUnit.SECONDS));
            assertTrue(first.contains("珠海")); assertFalse(first.contains("顺德"));
            assertTrue(second.contains("顺德")); assertFalse(second.contains("珠海"));
            assertEquals("天气", weather.get(10, TimeUnit.SECONDS).response);
            assertEquals(3, instances.size());
        }
    }

    @Test void eachSubscriptionAndNewConversationGetsFreshState() {
        AtomicInteger sessions = new AtomicInteger();
        ManagerAgent manager = new ManagerAgent((tool, edits, policy) -> {
            sessions.incrementAndGet();
            assertNull(tool.getLatestJourneyPlan());
            assertFalse(tool.wasTripPlannerCalled());
            return prompt -> answer("新的答复");
        });
        var stream = manager.stream("你好");
        assertEquals(0, sessions.get());
        stream.collectList().block(Duration.ofSeconds(5));
        stream.collectList().block(Duration.ofSeconds(5));
        manager.run("新对话");
        assertEquals(3, sessions.get());
    }

    @Test void cancelledRequestCannotContaminateNextRequest() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        ManagerAgent manager = new ManagerAgent((tool, edits, policy) -> prompt -> {
            if (prompt.startsWith("旧请求")) {
                entered.countDown();
                try { new CountDownLatch(1).await(); }
                catch (InterruptedException error) { interrupted.countDown(); Thread.currentThread().interrupt(); }
                setPlan(tool, "旧城市");
            }
            return answer("完成");
        });
        var subscription = manager.stream("旧请求").subscribe();
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        subscription.dispose();
        assertTrue(interrupted.await(5, TimeUnit.SECONDS));
        String events = String.join("", manager.stream("新请求").collectList().block(Duration.ofSeconds(5)));
        assertFalse(events.contains("旧城市"));
        assertFalse(events.contains("JOURNEY_PLAN"));
    }
}
