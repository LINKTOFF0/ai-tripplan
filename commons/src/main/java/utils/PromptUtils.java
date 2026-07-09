package utils;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;

import java.util.List;

public class PromptUtils {
    public Msg getUserMsg(String prompt){
        Msg userPrompt = Msg.builder()
                .role(MsgRole.USER)
                .content(List.of(
                        TextBlock.builder()
                                .text(prompt)
                                .build()
                ))
                .build();
        return userPrompt;
    }
}
