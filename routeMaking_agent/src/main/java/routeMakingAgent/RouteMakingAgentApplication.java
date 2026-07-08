package routeMakingAgent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import routeMakingAgent.mcp.BaiduMapMCP;

@SpringBootApplication
public class RouteMakingAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(RouteMakingAgentApplication.class, args);
    }
}
