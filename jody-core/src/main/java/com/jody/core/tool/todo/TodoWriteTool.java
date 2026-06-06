package com.jody.core.tool.todo;

import com.jody.core.deps.JodyDeps;
import com.jody.core.tool.JodyTool;
import com.jody.core.tool.ToolContext;

import java.util.*;

/**
 * Create and manage a structured task list for the current session.
 *
 */
public class TodoWriteTool implements JodyTool {

    @Override public String getName() { return "todo_write"; }

    @Override public String getDescription() {
        return "Create and manage a structured task list for your current coding session. "
                + "Use this to track progress, organize complex tasks, and demonstrate thoroughness.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("todos", Map.of("type", "string", "description",
                "JSON string of todo items array. Each item: id (string), status (pending/in_progress/completed), content (string)"));
        return props;
    }

    @Override public List<String> getRequiredParameters() { return List.of("todos"); }

    @Override
    public String execute(ToolContext ctx, Map<String, Object> arguments) {
        String todosJson = (String) arguments.get("todos");
        JodyDeps deps = ctx.getDeps(JodyDeps.class);
        List<Map<String, Object>> todoList = deps != null ? deps.getTodoList() : null;

        if (todoList == null) {
            return "[TODO_NOT_AVAILABLE] Todo list is not available";
        }

        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> newTodos = mapper.readValue(todosJson, List.class);

            // Merge new todos with existing list
            for (Map<String, Object> newTodo : newTodos) {
                String id = (String) newTodo.get("id");
                if (id == null) continue;

                // Find existing with same id
                boolean found = false;
                for (Map<String, Object> existing : todoList) {
                    if (id.equals(existing.get("id"))) {
                        existing.putAll(newTodo);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    todoList.add(new LinkedHashMap<>(newTodo));
                }
            }

            // Format output
            StringBuilder sb = new StringBuilder("Todo list updated:\n");
            for (Map<String, Object> item : todoList) {
                String status = (String) item.getOrDefault("status", "pending");
                String content = (String) item.getOrDefault("content", "");
                String checkbox = status.equals("completed") ? "[x]" : status.equals("in_progress") ? "[~]" : "[ ]";
                sb.append(checkbox).append(" ").append(content).append(" (").append(status).append(")\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "[TODO_ERROR] Failed to parse todos JSON: " + e.getMessage();
        }
    }
}
