package managerAgent;

import com.alibaba.nacos.api.exception.NacosException;
import managerAgent.agents.ManagerAgent;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import utils.EnvUtils;


@SpringBootApplication
public class ManagerAgentApplication {
    public static void main(String[] args) throws NacosException
    {
        // 从 classpath 加载 .env 文件到系统属性
        EnvUtils.loadEnv();

        SpringApplication.run(ManagerAgentApplication.class, args);
        /*ManagerAgent managerAgent = new ManagerAgent();
        managerAgent.run();*/
    }
}

