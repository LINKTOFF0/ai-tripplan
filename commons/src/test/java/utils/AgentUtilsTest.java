package utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AgentUtilsTest {
    @Test void agentInstructionsAreActuallyPassedAsSystemPrompt() {
        String old = System.getProperty("DEEPSEEK_API_KEY");
        try {
            System.setProperty("DEEPSEEK_API_KEY", "offline-test-placeholder");
            var agent = AgentUtils.getReActAgentBuilder("test", "仅按需调用地图工具").build();
            assertEquals("仅按需调用地图工具", agent.getSysPrompt());
        } finally {
            if (old == null) System.clearProperty("DEEPSEEK_API_KEY");
            else System.setProperty("DEEPSEEK_API_KEY", old);
        }
    }
}
