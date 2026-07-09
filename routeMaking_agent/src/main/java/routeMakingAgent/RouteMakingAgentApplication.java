package routeMakingAgent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import routeMakingAgent.mcp.BaiduMapMCP;
import utils.EnvUtils;

@SpringBootApplication
public class RouteMakingAgentApplication {
    public static void main(String[] args) {
        // 从 classpath 加载 .env 文件到系统属性
        EnvUtils.loadEnv();

        SpringApplication.run(RouteMakingAgentApplication.class, args);
    }
}
