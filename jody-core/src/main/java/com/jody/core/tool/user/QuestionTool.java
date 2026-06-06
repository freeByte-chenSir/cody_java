package com.jody.core.tool.user;

import com.jody.core.deps.JodyDeps;
import com.jody.core.interaction.InteractionHandler;
import com.jody.core.interaction.InteractionHandler.InteractionRequest;
import com.jody.core.interaction.InteractionHandler.InteractionResponse;
import com.jody.core.tool.JodyTool;
import com.jody.core.tool.ToolContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Ask the user a question during agent execution.
 *
 */
public class QuestionTool implements JodyTool {

    @Override public String getName() { return "question"; }

    @Override public String getDescription() {
        return "Ask the user a question to gather information or confirm an action.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("question", Map.of("type", "string", "description", "The question to ask the user"));
        return props;
    }

    @Override public List<String> getRequiredParameters() { return List.of("question"); }

    @Override
    public String execute(ToolContext ctx, Map<String, Object> arguments) {
        String question = (String) arguments.get("question");
        JodyDeps deps = ctx.getDeps(JodyDeps.class);

        InteractionHandler handler = deps != null ? deps.getInteractionHandler() : null;
        if (handler == null) {
            return "[INTERACTION_UNAVAILABLE] No interaction handler configured. "
                    + "Question: " + question;
        }

        try {
            InteractionRequest request = new InteractionRequest(
                    InteractionRequest.Kind.QUESTION,
                    question,
                    null, null, 0.0
            );

            var future = handler.handle(request);
            InteractionResponse response = future.get(120, TimeUnit.SECONDS);

            if (response != null && response.getAction() == InteractionResponse.Action.ANSWER) {
                return "[USER_ANSWER] " + response.getContent();
            }
            return "[USER_NO_ANSWER] User did not provide an answer";
        } catch (Exception e) {
            return "[INTERACTION_UNAVAILABLE] Failed to get user response: " + e.getMessage();
        }
    }
}
