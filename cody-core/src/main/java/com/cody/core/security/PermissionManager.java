package com.cody.core.security;

import com.cody.core.config.Config;
import com.cody.core.deps.CodyDeps;
import com.cody.core.error.CodyErrors.ToolPermissionDenied;
import com.cody.core.interaction.InteractionHandler;
import com.cody.core.interaction.InteractionHandler.InteractionRequest;
import com.cody.core.interaction.InteractionHandler.InteractionResponse;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Tool-level permission checking with allow/deny/confirm rules.
 *
 */
public class PermissionManager {

    private final Config config;

    public PermissionManager(Config config) {
        this.config = config;
    }

    public void checkPermission(CodyDeps deps, String toolName, String argsSummary) {
        Map<String, String> overrides = config.getPermissions().getOverrides();
        if (overrides.containsKey(toolName)) {
            String level = overrides.get(toolName);
            if ("allow".equals(level)) return;
            if ("deny".equals(level)) {
                throw new ToolPermissionDenied("Tool '" + toolName + "' is denied by config");
            }
        }

        Set<String> autoApproved = deps.getAutoApprovedTools();
        if (autoApproved.contains(toolName)) return;

        String defaultLevel = config.getPermissions().getDefaultLevel();
        if ("allow".equals(defaultLevel)) return;
        if ("deny".equals(defaultLevel)) {
            throw new ToolPermissionDenied("Tool '" + toolName + "' requires confirmation but default is deny");
        }

        InteractionHandler handler = deps.getInteractionHandler();
        if (handler == null || !config.getInteraction().isEnabled()) {
            throw new ToolPermissionDenied(
                    "Tool '" + toolName + "' requires confirmation but no interaction handler is configured");
        }

        InteractionRequest request = new InteractionRequest(
                InteractionRequest.Kind.CONFIRM,
                "Allow tool '" + toolName + "' with args: " + argsSummary + "?",
                null, null, 0.0
        );

        try {
            CompletableFuture<InteractionResponse> future = handler.handle(request);
            double timeout = config.getInteraction().getTimeout();
            InteractionResponse response = future.get((long) (timeout * 1000), TimeUnit.MILLISECONDS);
            if (response == null || response.getAction() != InteractionResponse.Action.APPROVE) {
                throw new ToolPermissionDenied("Tool '" + toolName + "' was not approved by user");
            }
        } catch (TimeoutException e) {
            throw new ToolPermissionDenied("Confirmation timed out for tool '" + toolName + "'");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ToolPermissionDenied("Confirmation interrupted for tool '" + toolName + "'");
        } catch (ExecutionException e) {
            throw new ToolPermissionDenied("Confirmation failed: " + e.getMessage());
        }
    }
}
