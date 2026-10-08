package data;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PromptSchema {
    private String prompt;
    private JourneyPlanDto journeyPlan;
    private String activeDayId;
    private java.util.List<ConversationMessageDto> history;
    private String task;
}
