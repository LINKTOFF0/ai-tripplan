package data;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.ArrayList;
import java.util.List;

public class JourneyPlanDto {
    @JsonPropertyDescription("本次行程的稳定标识")
    public String id;
    @JsonPropertyDescription("行程标题")
    public String title;
    @JsonPropertyDescription("行程目的地城市或区域")
    public String destination;
    @JsonPropertyDescription("按日期和游览顺序排列的每日行程")
    public List<JourneyDayDto> days = new ArrayList<>();

    public JourneyPlanDto() {}
}
