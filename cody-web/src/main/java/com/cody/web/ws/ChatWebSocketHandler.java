package com.cody.web.ws;

import com.cody.core.runner.StreamEvent;
import com.cody.sdk.CodyClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.util.*;

/**
 * WebSocket handler for bidirectional chat.
 *
 * WebSocket endpoint: /ws
 * Messages: JSON with "prompt" field.
 * Responses: JSON stream events (text_delta, tool_call, tool_result, done, error).
 */
public class ChatWebSocketHandler extends AbstractWebSocketHandler {

    private final CodyClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public ChatWebSocketHandler(CodyClient client) {
        this.client = client;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // Ready to receive messages
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(payload, Map.class);
        // Frontend sends { "type": "message", "content": "..." }
        // CLI/simple clients send { "prompt": "..." }
        String rawPrompt = (String) body.getOrDefault("prompt", "");
        if (rawPrompt.isEmpty()) {
            rawPrompt = (String) body.getOrDefault("content", "");
        }
        final String prompt = rawPrompt;

        if (prompt.isEmpty()) {
            session.sendMessage(new TextMessage(mapper.writeValueAsString(
                    Map.of("type", "error", "message", "No prompt provided"))));
            return;
        }

        // Stream results back over WebSocket
        Thread producer = new Thread(() -> {
            try {
                Iterator<StreamEvent> events = client.stream(prompt);
                while (events.hasNext()) {
                    StreamEvent ev = events.next();
                    if (ev == StreamEvent.POISON_PILL) break;

                    Map<String, Object> msg = new LinkedHashMap<>();
                    msg.put("type", ev.getType());

                    switch (ev.getType()) {
                        case StreamEvent.TEXT_DELTA ->
                                msg.put("content", ((StreamEvent.TextDelta) ev).getContent());
                        case StreamEvent.TOOL_CALL -> {
                            StreamEvent.ToolCall tc = (StreamEvent.ToolCall) ev;
                            msg.put("tool_name", tc.getToolName());
                            msg.put("args", tc.getArgs());
                        }
                        case StreamEvent.TOOL_RESULT -> {
                            StreamEvent.ToolResult tr = (StreamEvent.ToolResult) ev;
                            msg.put("tool_name", tr.getToolName());
                            msg.put("content", tr.getContent());
                        }
                        case StreamEvent.DONE -> {
                            StreamEvent.Done d = (StreamEvent.Done) ev;
                            msg.put("output", d.getOutput());
                            msg.put("input_tokens", d.getInputTokens());
                            msg.put("output_tokens", d.getOutputTokens());
                        }
                        case StreamEvent.ERROR ->
                                msg.put("message", ((StreamEvent.Error) ev).getMessage());
                    }

                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(mapper.writeValueAsString(msg)));
                    }
                }
            } catch (Exception e) {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(mapper.writeValueAsString(
                                Map.of("type", "error", "message", e.getMessage()))));
                    }
                } catch (Exception ignored) {}
            }
        }, "ws-stream-producer");
        producer.setDaemon(true);
        producer.start();
    }
}
