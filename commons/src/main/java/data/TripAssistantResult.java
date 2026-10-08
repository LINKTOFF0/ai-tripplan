package data;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class TripAssistantResult {
    @JsonPropertyDescription("面向用户的中文 Markdown 答复，不包含原始 JSON")
    public String answerMarkdown;

    @JsonPropertyDescription("用户明确要求制定或修改行程时返回结构化计划，否则返回 null")
    public JourneyPlanDto journeyPlan;

    public TripAssistantResult() {}
}
