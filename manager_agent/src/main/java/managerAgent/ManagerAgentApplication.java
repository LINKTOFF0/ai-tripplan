package managerAgent;

import com.alibaba.nacos.api.exception.NacosException;
import managerAgent.agents.ManagerAgent;
import org.springframework.boot.autoconfigure.SpringBootApplication;


//@SpringBootApplication
public class ManagerAgentApplication {
    public static void main(String[] args) throws NacosException
    {

        ManagerAgent managerAgent = new ManagerAgent();
        managerAgent.run();
    }
}

