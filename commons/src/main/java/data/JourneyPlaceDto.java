package data;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class JourneyPlaceDto {
    @JsonPropertyDescription("地点在计划内的稳定标识")
    public String id;
    @JsonPropertyDescription("高德地图可搜索的真实地点名称，不得虚构")
    public String name;
    @JsonPropertyDescription("地点所属城市或区域")
    public String city;
    @JsonPropertyDescription("真实或可供地图检索的详细地址")
    public String address;
    @JsonPropertyDescription("地点类别，只能是 attraction、food、hotel、transport、other")
    public String category;
    @JsonPropertyDescription("建议开始时间，未知时留空")
    public String startTime;
    @JsonPropertyDescription("建议停留时长，单位分钟")
    public Integer durationMinutes;
    @JsonPropertyDescription("简短的地点安排说明")
    public String description;

    public JourneyPlaceDto() {}
}
