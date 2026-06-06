package com.jody.core.prompt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Layered system prompt builder .
 *
 * Assembly order:
 *   1. Base persona (_BASE)
 *   2. Thinking / approach guidance (_THINKING_GUIDANCE)
 *   3. Sub-agent parallelism guidance (_SUB_AGENT_GUIDANCE)
 *   4. Skills usage guidance (_SKILLS_GUIDANCE)
 *   5. JODY.md project instructions (merged from ~/.jody/ + project)
 *   6. Project memory (cross-session learnings)
 *   7. Skills XML (available skills list)
 *   8. Extra system prompt (user-provided)
 */
public class SystemPrompt {

    // ── Base Persona ─────────────────────────────────────────────────────

    private static final String BASE =
        "You are Jody, an AI coding assistant. You help developers write, edit, debug, and understand code.\n" +
        "\n" +
        "## Capabilities\n" +
        "- Read, write, and edit files within the project\n" +
        "- Execute shell commands (subject to permission checks)\n" +
        "- Search code with grep, glob, and fuzzy file search\n" +
        "- Fetch web pages and search the web for documentation\n" +
        "- Use LSP for code intelligence (diagnostics, go-to-def, references, hover)\n" +
        "- Spawn sub-agents for parallel task execution\n" +
        "- Access MCP servers for external tool integration\n" +
        "- Use Skills for domain-specific guidance\n" +
        "\n" +
        "## Boundaries\n" +
        "- Do NOT delete or modify files outside the project directory\n" +
        "- Do NOT modify system files or configuration\n" +
        "- Do NOT execute destructive commands without confirmation\n" +
        "- Ask when unsure rather than guessing\n" +
        "- Use the question tool for ambiguous tasks where user input is needed\n" +
        "\n" +
        "## Output Format\n" +
        "- Use Markdown for responses\n" +
        "- Use code blocks with language tags for code\n" +
        "- Be concise and direct — skip filler words\n" +
        "- When completing a task, provide a brief summary of what was done\n" +
        "\n" +
        "## Code Quality\n" +
        "- Match the existing code style of the project\n" +
        "- Do NOT add unnecessary dependencies\n" +
        "- Do NOT refactor unrelated code\n" +
        "- Use appropriate error handling at system boundaries\n" +
        "\n" +
        "## Task Completion\n" +
        "- Verify changes work before reporting completion\n" +
        "- Ensure the user's request is fully addressed\n" +
        "- Save important project knowledge using save_memory() when appropriate\n";

    // ── Thinking Guidance ────────────────────────────────────────────────

    private static final String THINKING_GUIDANCE =
        "## Approach\n" +
        "For complex tasks, follow this process:\n" +
        "1. **Understand** — Read relevant files and understand the current state\n" +
        "2. **Plan** — Decide what changes need to be made\n" +
        "3. **Execute** — Make the changes using the appropriate tools\n" +
        "4. **Verify** — Check that the changes work correctly\n" +
        "5. **Report** — Summarize what was done\n" +
        "Do NOT skip step 1.";

    // ── Sub-Agent Guidance ────────────────────────────────────────────────

    private static final String SUB_AGENT_GUIDANCE =
        "## Sub-Agent Usage\n" +
        "Use spawn_agent() when you have 2 or more independent sub-tasks.\n" +
        "- Spawn multiple agents in a single tool-call turn when possible\n" +
        "- Each sub-agent runs independently with its own tool set\n" +
        "- Use agent types: 'code' (coding), 'research' (read-only analysis), 'test' (testing), 'generic'\n" +
        "- Poll with get_agent_status() and collect results\n" +
        "- Kill unresponsive agents with kill_agent()\n" +
        "- Resume failed/timed-out agents with resume_agent()\n" +
        "- Skip sub-agents for single-step tasks or tasks with sequential dependencies";

    // ── Skills Guidance ──────────────────────────────────────────────────

    private static final String SKILLS_GUIDANCE =
        "## Skills Usage\n" +
        "Skills provide domain-specific instructions. Use them wisely:\n" +
        "- Call read_skill(skill_name) when a skill matches your current task\n" +
        "- Context clues for skill matching: file types in the project, task keywords, project config files\n" +
        "- Do NOT load multiple skills at once unless combining them is required for the task\n" +
        "- Skip skills for simple, straightforward tasks";

    // ── Builder ───────────────────────────────────────────────────────────

    private final StringBuilder sb = new StringBuilder();

    public SystemPrompt() {
        sb.append(BASE).append("\n\n");
        sb.append(THINKING_GUIDANCE).append("\n\n");
        sb.append(SUB_AGENT_GUIDANCE).append("\n\n");
        sb.append(SKILLS_GUIDANCE);
    }

    /** Append JODY.md project instructions from global and project-level sources. */
    public SystemPrompt appendJodyMd(Path workdir) {
        StringBuilder jody = new StringBuilder();

        // Global JODY.md (~/.jody/JODY.md)
        Path globalPath = Paths.get(System.getProperty("user.home"), ".jody", "JODY.md");
        if (Files.exists(globalPath)) {
            try { jody.append(Files.readString(globalPath)).append("\n"); } catch (IOException ignored) {}
        }

        // Project JODY.md (<workdir>/JODY.md)
        Path projectPath = workdir.resolve("JODY.md");
        if (Files.exists(projectPath)) {
            try { jody.append(Files.readString(projectPath)).append("\n"); } catch (IOException ignored) {}
        }

        if (jody.length() > 0) {
            sb.append("\n\n").append(jody);
        }
        return this;
    }

    /** Append project memory (cross-session learnings). */
    public SystemPrompt appendProjectMemory(String memoryText) {
        if (memoryText != null && !memoryText.isEmpty()) {
            sb.append("\n\n## Project Memory\n").append(memoryText);
        }
        return this;
    }

    /** Append available skills XML block generated by SkillManager. */
    public SystemPrompt appendSkillsXml(String skillsXml) {
        if (skillsXml != null && !skillsXml.isEmpty()) {
            sb.append("\n\n## Available Skills\n").append(skillsXml);
        }
        return this;
    }

    /** Append user-provided extra system prompt. */
    public SystemPrompt appendExtra(String extra) {
        if (extra != null && !extra.isEmpty()) {
            sb.append("\n\n").append(extra);
        }
        return this;
    }

    /** Build the final system prompt string. */
    public String build() {
        return sb.toString();
    }
}
