package com.jody.core.tool;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Tool interface for all agent tools in the framework.
 *
 * Each tool provides metadata (name, description, parameter schema) and an
 * {@code execute} method. Tools are registered declaratively in
 * {@link com.jody.core.tool.ToolRegistry} and converted to LangChain4j
 * {@link dev.langchain4j.agent.tool.ToolSpecification} for LLM integration.
 */
public interface JodyTool {

    /** Unique tool name used in LLM tool_use requests. */
    String getName();

    /** Description for the LLM. */
    String getDescription();

    /**
     * JSON Schema for parameters. Each key is a property name,
     * each value is a map with "type", "description", and optionally "enum", "default".
     */
    Map<String, Object> getParametersSchema();

    /** List of required parameter names. */
    default java.util.List<String> getRequiredParameters() {
        return java.util.List.of();
    }

    /**
     * Execute the tool with given arguments and context.
     *
     * @param ctx       Tool execution context (workdir, tool name, deps)
     * @param arguments Raw arguments from the LLM's tool call
     * @return String result to send back to the LLM
     */
    String execute(ToolContext ctx, Map<String, Object> arguments);

    /**
     * Convert this tool to a LangChain4j ToolSpecification (for LLM request building).
     */
    default ToolSpecification toSpecification() {
        dev.langchain4j.agent.tool.ToolSpecification.Builder builder = ToolSpecification.builder()
                .name(getName())
                .description(getDescription());

        Map<String, Object> props = getParametersSchema();
        if (props != null) {
            for (Map.Entry<String, Object> entry : props.entrySet()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> propDef = (Map<String, Object>) entry.getValue();
                String propType = (String) propDef.getOrDefault("type", "string");
                String propDesc = (String) propDef.getOrDefault("description", "");
                builder.addParameter(entry.getKey(), dev.langchain4j.agent.tool.JsonSchemaProperty.type(propType), dev.langchain4j.agent.tool.JsonSchemaProperty.description(propDesc));
            }
        }

        return builder.build();
    }

    /**
     * Convert to a LangChain4j tool specification in the standard format.
     * This matches what Anthropic/OpenAI expect.
     */
    default Map<String, Object> toAnthropicToolDef() {
        Map<String, Object> props = getParametersSchema();
        java.util.List<String> required = getRequiredParameters();

        Map<String, Object> inputSchema = new java.util.LinkedHashMap<>();
        inputSchema.put("type", "object");
        inputSchema.put("properties", props);
        if (!required.isEmpty()) {
            inputSchema.put("required", required);
        }

        Map<String, Object> toolDef = new java.util.LinkedHashMap<>();
        toolDef.put("name", getName());
        toolDef.put("description", getDescription());
        toolDef.put("input_schema", inputSchema);

        return toolDef;
    }
}
