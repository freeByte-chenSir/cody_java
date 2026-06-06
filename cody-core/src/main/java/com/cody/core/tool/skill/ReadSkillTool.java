package com.cody.core.tool.skill;

import com.cody.core.deps.CodyDeps;
import com.cody.core.tool.CodyTool;
import com.cody.core.tool.ToolContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads a skill's full instructions by name.
 *
 */
public class ReadSkillTool implements CodyTool {

    @Override public String getName() { return "read_skill"; }

    @Override public String getDescription() {
        return "Load the full instructions for a skill by name.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("name", Map.of("type", "string", "description", "Name of the skill to load"));
        return props;
    }

    @Override public List<String> getRequiredParameters() { return List.of("name"); }

    @Override
    public String execute(ToolContext ctx, Map<String, Object> arguments) {
        String name = (String) arguments.get("name");
        CodyDeps deps = ctx.getDeps(CodyDeps.class);
        Object skillManager = deps != null ? deps.getSkillManager() : null;

        if (skillManager == null) {
            return "[SKILL_MANAGER_NOT_AVAILABLE] Skill manager is not configured.";
        }

        try {
            Object result = skillManager.getClass().getMethod("readSkill", String.class)
                    .invoke(skillManager, name);
            if (result == null) {
                return "[SKILL_NOT_FOUND] Skill '" + name + "' not found.";
            }
            return result.toString();
        } catch (java.lang.reflect.InvocationTargetException e) {
            return "[SKILL_NOT_FOUND] Skill '" + name + "' not found: " + e.getCause().getMessage();
        } catch (Exception e) {
            return "[SKILL_MANAGER_NOT_AVAILABLE] Cannot read skill '" + name + "': " + e.getMessage();
        }
    }
}
