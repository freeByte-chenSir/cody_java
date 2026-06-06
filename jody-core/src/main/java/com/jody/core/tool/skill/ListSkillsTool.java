package com.jody.core.tool.skill;

import com.jody.core.deps.JodyDeps;
import com.jody.core.tool.JodyTool;
import com.jody.core.tool.ToolContext;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Returns available skills list from SkillManager.
 *
 */
public class ListSkillsTool implements JodyTool {

    @Override public String getName() { return "list_skills"; }

    @Override public String getDescription() {
        return "List all available skills from the skill manager.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return new LinkedHashMap<>(); // no parameters
    }

    @Override
    public String execute(ToolContext ctx, Map<String, Object> arguments) {
        JodyDeps deps = ctx.getDeps(JodyDeps.class);
        Object skillManager = deps != null ? deps.getSkillManager() : null;

        if (skillManager == null) {
            return "[SKILL_MANAGER_NOT_AVAILABLE] Skill manager is not configured.";
        }

        // SkillManager should have a listSkills() or similar method.
        // Since the exact API is not yet defined, return a placeholder.
        try {
            Object result = skillManager.getClass().getMethod("listSkills").invoke(skillManager);
            return result != null ? result.toString() : "[]";
        } catch (Exception e) {
            return "[SKILL_MANAGER_NOT_AVAILABLE] Skill manager available but listSkills() not yet callable: "
                    + e.getMessage();
        }
    }
}
