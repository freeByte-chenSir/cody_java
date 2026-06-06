package com.jody.core.tool.todo;

import com.jody.core.deps.JodyDeps;
import com.jody.core.tool.JodyTool;
import com.jody.core.tool.ToolContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read the current todo list.
 *
 */
public class TodoReadTool implements JodyTool {

    @Override public String getName() { return "todo_read"; }

    @Override public String getDescription() {
        return "Read the current todo list for this session.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return new LinkedHashMap<>(); // no parameters
    }

    @Override
    public String execute(ToolContext ctx, Map<String, Object> arguments) {
        JodyDeps deps = ctx.getDeps(JodyDeps.class);
        List<Map<String, Object>> todoList = deps != null ? deps.getTodoList() : null;

        if (todoList == null || todoList.isEmpty()) {
            return "(no todos)";
        }

        StringBuilder sb = new StringBuilder("Current todo list:\n");
        for (Map<String, Object> item : todoList) {
            String status = (String) item.getOrDefault("status", "pending");
            String content = (String) item.getOrDefault("content", "");
            String checkbox = status.equals("completed") ? "[x]" : status.equals("in_progress") ? "[~]" : "[ ]";
            sb.append(checkbox).append(" ").append(content).append(" (").append(status).append(")\n");
        }
        return sb.toString().trim();
    }
}
