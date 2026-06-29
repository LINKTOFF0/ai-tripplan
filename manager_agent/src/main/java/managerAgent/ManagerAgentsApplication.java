package managerAgent;

import managerAgent.agents.ManagerAgent;
import org.springframework.boot.autoconfigure.SpringBootApplication;

//@SpringBootApplication
public class ManagerAgentsApplication {
    public static void main(String[] args){
        ManagerAgent manager = new ManagerAgent();
        manager.run();
        //SpringApplication.run(ManagerAgentsApplication.class, args);
    }
}
