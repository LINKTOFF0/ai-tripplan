package managerAgent.agents;

import java.util.Set;

public enum RequestPolicy {
    GENERAL(Set.of(), Set.of(), false, false),
    WEATHER(Set.of("weather"), Set.of(), false, false),
    PLACE_SEARCH(Set.of("place"), Set.of(), false, false),
    ROUTE_QUERY(Set.of("route"), Set.of(), false, false),
    EDIT_DATE(Set.of(), Set.of("updateDayDate"), false, false),
    EDIT_ADVICE(Set.of(), Set.of("updatePlaceAdvice"), false, false),
    ADD_PLACE(Set.of("place"), Set.of("addJourneyPlace"), false, false),
    EDIT_ORDER(Set.of(), Set.of("reorderDayPlaces"), false, false),
    ROUTE_OPTIMIZATION(Set.of("place", "route"), Set.of("reorderDayPlaces"), true, false),
    PLAN(Set.of("place", "route", "weather"), Set.of(), true, true);

    public final Set<String> mapCapabilities;
    public final Set<String> editTools;
    public final boolean routeAgent;
    public final boolean plannerAgent;

    RequestPolicy(Set<String> mapCapabilities, Set<String> editTools, boolean routeAgent, boolean plannerAgent) {
        this.mapCapabilities = mapCapabilities; this.editTools = editTools;
        this.routeAgent = routeAgent; this.plannerAgent = plannerAgent;
    }

    public static RequestPolicy resolve(String explicitTask, String prompt) {
        if (explicitTask != null && !explicitTask.isBlank()) {
            try { return valueOf(explicitTask); }
            catch (IllegalArgumentException error) { throw new IllegalArgumentException("任务类型无效。"); }
        }
        String text = prompt == null ? "" : prompt;
        boolean edit = text.matches("(?s).*(修改|改|调整|设置|清空|更新).*" );
        if (text.matches("(?s).*(优化.*(路线|线路)|(路线|线路).*优化).*")) return ROUTE_OPTIMIZATION;
        if (text.matches("(?s).*(顺序|排序).*" ) && text.matches("(?s).*(调换|交换|改|调整|重排|排序).*")) return EDIT_ORDER;
        if (edit && text.matches("(?s).*(日期|第.{1,5}天|[0-9一二三四五六七八九十]+月.*[日号]).*")) return EDIT_DATE;
        if (edit && text.matches("(?s).*(建议|备注).*")) return EDIT_ADVICE;
        if (text.matches("(?s).*(添加|加入|加到|加进|新增).*")) return ADD_PLACE;
        if (text.matches("(?s).*(规划.*(行程|旅行|旅游)|生成.*行程|安排.*行程|[一二三四五六七八九十0-9]+日游).*")) return PLAN;
        if (text.matches("(?s).*(天气|气温|下雨|降雨).*")) return WEATHER;
        if (text.matches("(?s).*(怎么去|如何去|怎么走|路线|路程|交通|步行|骑行|驾车|公交).*")) return ROUTE_QUERY;
        if (text.matches("(?s).*(查找|搜索|附近|推荐.*(景点|餐厅|酒店)|去哪逛).*")) return PLACE_SEARCH;
        return GENERAL;
    }
}
