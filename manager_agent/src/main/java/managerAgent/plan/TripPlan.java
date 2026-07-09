package managerAgent.plan;

import io.agentscope.core.plan.PlanNotebook;

//Agent自主分解旅游规划任务
public class TripPlan {
    //自定义 PlantNotebook 实例
    public PlanNotebook getPlan(){
        return PlanNotebook.builder()
                //计划步骤是否需要用户确认（关闭：当前无用户交互处理，开启会阻塞流程）
                .needUserConfirm(false)
                //分解出来的子任务数量限制
                .maxSubtasks(5)
                //计划的存储方式
                //.storage()
                .build();
    }
}
