package data;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.ArrayList;
import java.util.List;

public class JourneyDayDto {
    @JsonPropertyDescription("该日的稳定标识")
    public String id;
    @JsonPropertyDescription("从 1 开始的行程天数序号")
    public Integer dayNumber;
    @JsonPropertyDescription("日期，未知时使用空字符串，格式 YYYY-MM-DD")
    public String date;
    @JsonPropertyDescription("当天安排主题")
    public String title;
    @JsonPropertyDescription("当天按访问顺序排列的地点")
    public List<JourneyPlaceDto> places = new ArrayList<>();

    public JourneyDayDto() {}
}
