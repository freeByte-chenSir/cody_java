package com.jody.core.tool;

import com.jody.core.tool.agent.*;
import com.jody.core.tool.command.ExecCommandTool;
import com.jody.core.tool.file.*;
import com.jody.core.tool.history.*;
import com.jody.core.tool.lsp.*;
import com.jody.core.tool.mcp.*;
import com.jody.core.tool.memory.SaveMemoryTool;
import com.jody.core.tool.search.*;
import com.jody.core.tool.skill.*;
import com.jody.core.tool.todo.*;
import com.jody.core.tool.user.QuestionTool;
import com.jody.core.tool.web.*;
import dev.langchain4j.agent.tool.ToolSpecification;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Declarative tool registry .
 *
 * Tools are organized in categorized lists (FILE_TOOLS, SEARCH_TOOLS, etc.).
 * CORE_TOOLS = all except MCP. SUB_AGENT_TOOLSETS maps agent type to tool subset.
 *
 * Adding a new tool requires:
 *   1. Create the tool class (implementing {@link JodyTool})
 *   2. Add it to the appropriate *_TOOLS list below
 *   3. Optionally add to SUB_AGENT_TOOLSETS
 */
public class ToolRegistry {

    // ── Tool Category Lists ──────────────────────────────────────────────

    /** File I/O tools: read, write, edit, list */
    public static final List<JodyTool> FILE_TOOLS = new ArrayList<>();
    /** Search tools: grep, glob, patch, search_files */
    public static final List<JodyTool> SEARCH_TOOLS = new ArrayList<>();
    /** Shell command execution */
    public static final List<JodyTool> COMMAND_TOOLS = new ArrayList<>();
    /** Skill management: list_skills, read_skill */
    public static final List<JodyTool> SKILL_TOOLS = new ArrayList<>();
    /** Sub-agent management: spawn, status, kill, resume */
    public static final List<JodyTool> SUB_AGENT_TOOLS = new ArrayList<>();
    /** MCP tools: mcp_call, mcp_list_tools */
    public static final List<JodyTool> MCP_TOOLS = new ArrayList<>();
    /** Web tools: webfetch, websearch */
    public static final List<JodyTool> WEB_TOOLS = new ArrayList<>();
    /** LSP tools: diagnostics, definition, references, hover */
    public static final List<JodyTool> LSP_TOOLS = new ArrayList<>();
    /** File history: undo, redo, list_file_changes */
    public static final List<JodyTool> FILE_HISTORY_TOOLS = new ArrayList<>();
    /** Task management: todo_write, todo_read */
    public static final List<JodyTool> TODO_TOOLS = new ArrayList<>();
    /** User interaction: question */
    public static final List<JodyTool> USER_TOOLS = new ArrayList<>();
    /** Memory: save_memory */
    public static final List<JodyTool> MEMORY_TOOLS = new ArrayList<>();

    /** All core tools (everything except MCP). */
    public static final List<JodyTool> CORE_TOOLS = new ArrayList<>();

    // ── Sub-Agent Toolsets ────────────────────────────────────────────────

    /** Maps agent type to its restricted tool subset. */
    public static final Map<String, List<JodyTool>> SUB_AGENT_TOOLSETS = new LinkedHashMap<>();

    static {
        // ── Register all tools ────────────────────────────────────────────

        // File tools
        register(new ReadFileTool(), FILE_TOOLS);
        register(new WriteFileTool(), FILE_TOOLS);
        register(new EditFileTool(), FILE_TOOLS);
        register(new ListDirectoryTool(), FILE_TOOLS);

        // Search tools
        register(new GrepTool(), SEARCH_TOOLS);
        register(new GlobTool(), SEARCH_TOOLS);
        register(new SearchFilesTool(), SEARCH_TOOLS);
        register(new PatchTool(), SEARCH_TOOLS);

        // Command tools
        register(new ExecCommandTool(), COMMAND_TOOLS);

        // Skill tools
        register(new ListSkillsTool(), SKILL_TOOLS);
        register(new ReadSkillTool(), SKILL_TOOLS);

        // Sub-agent tools
        register(new SpawnAgentTool(), SUB_AGENT_TOOLS);
        register(new GetAgentStatusTool(), SUB_AGENT_TOOLS);
        register(new KillAgentTool(), SUB_AGENT_TOOLS);
        register(new ResumeAgentTool(), SUB_AGENT_TOOLS);

        // MCP tools
        register(new McpCallTool(), MCP_TOOLS);
        register(new McpListToolsTool(), MCP_TOOLS);

        // Web tools
        register(new WebFetchTool(), WEB_TOOLS);
        register(new WebSearchTool(), WEB_TOOLS);

        // LSP tools
        register(new LspDiagnosticsTool(), LSP_TOOLS);
        register(new LspDefinitionTool(), LSP_TOOLS);
        register(new LspReferencesTool(), LSP_TOOLS);
        register(new LspHoverTool(), LSP_TOOLS);

        // File history tools
        register(new UndoFileTool(), FILE_HISTORY_TOOLS);
        register(new RedoFileTool(), FILE_HISTORY_TOOLS);
        register(new ListFileChangesTool(), FILE_HISTORY_TOOLS);

        // Todo tools
        register(new TodoWriteTool(), TODO_TOOLS);
        register(new TodoReadTool(), TODO_TOOLS);

        // User tools
        register(new QuestionTool(), USER_TOOLS);

        // Memory tools
        register(new SaveMemoryTool(), MEMORY_TOOLS);

        // ── Sub-agent toolsets ───────────────────────────────────────────
        SUB_AGENT_TOOLSETS.put("research", new ArrayList<>());
        SUB_AGENT_TOOLSETS.put("test", new ArrayList<>());
        SUB_AGENT_TOOLSETS.put("code", new ArrayList<>());
        SUB_AGENT_TOOLSETS.put("generic", new ArrayList<>());

        buildSubAgentToolsets();
    }

    // ── Registration API ──────────────────────────────────────────────────

    /**
     * Register a tool in its category and rebuild CORE_TOOLS.
     * Called once per tool at application startup.
     */
    public static void register(JodyTool tool, List<JodyTool> category) {
        category.add(tool);
        rebuildCoreTools();
    }

    /** Rebuild CORE_TOOLS from all categories except MCP. */
    private static void rebuildCoreTools() {
        CORE_TOOLS.clear();
        CORE_TOOLS.addAll(FILE_TOOLS);
        CORE_TOOLS.addAll(SEARCH_TOOLS);
        CORE_TOOLS.addAll(COMMAND_TOOLS);
        CORE_TOOLS.addAll(SKILL_TOOLS);
        CORE_TOOLS.addAll(SUB_AGENT_TOOLS);
        CORE_TOOLS.addAll(WEB_TOOLS);
        CORE_TOOLS.addAll(LSP_TOOLS);
        CORE_TOOLS.addAll(FILE_HISTORY_TOOLS);
        CORE_TOOLS.addAll(TODO_TOOLS);
        CORE_TOOLS.addAll(USER_TOOLS);
        CORE_TOOLS.addAll(MEMORY_TOOLS);
    }

    /**
     * Build sub-agent toolset by merging listed tool categories.
     * Called after all tools are registered.
     */
    public static void buildSubAgentToolsets() {
        // Research: read-only file ops + search
        List<JodyTool> research = SUB_AGENT_TOOLSETS.get("research");
        // Only add read-only tools
        for (JodyTool t : FILE_TOOLS) {
            String name = t.getName();
            if ("read_file".equals(name) || "list_directory".equals(name)) {
                research.add(t);
            }
        }
        research.addAll(SEARCH_TOOLS);

        // Test: read + write + edit + exec (no agent tools)
        List<JodyTool> test = SUB_AGENT_TOOLSETS.get("test");
        test.addAll(FILE_TOOLS);
        test.addAll(SEARCH_TOOLS);
        test.addAll(COMMAND_TOOLS);

        // Code / Generic: full file + search + command
        List<JodyTool> code = SUB_AGENT_TOOLSETS.get("code");
        code.addAll(FILE_TOOLS);
        code.addAll(SEARCH_TOOLS);
        code.addAll(COMMAND_TOOLS);

        List<JodyTool> generic = SUB_AGENT_TOOLSETS.get("generic");
        generic.addAll(FILE_TOOLS);
        generic.addAll(SEARCH_TOOLS);
        generic.addAll(COMMAND_TOOLS);
    }

    /**
     * Get all tools for an agent, optionally including MCP and custom tools,
     * with optional include/exclude filtering.
     */
    public static List<JodyTool> getTools(boolean includeMcp, List<JodyTool> customTools,
                                           Set<String> includeTools, Set<String> excludeTools) {
        List<JodyTool> all = new ArrayList<>(CORE_TOOLS);
        if (includeMcp) all.addAll(MCP_TOOLS);
        if (customTools != null) all.addAll(customTools);

        if (includeTools != null && !includeTools.isEmpty()) {
            all = all.stream().filter(t -> includeTools.contains(t.getName())).collect(Collectors.toList());
        }
        if (excludeTools != null && !excludeTools.isEmpty()) {
            all = all.stream().filter(t -> !excludeTools.contains(t.getName())).collect(Collectors.toList());
        }
        return all;
    }

    /** Get tools for a sub-agent type. */
    public static List<JodyTool> getSubAgentTools(String agentType) {
        return SUB_AGENT_TOOLSETS.getOrDefault(agentType, SUB_AGENT_TOOLSETS.get("generic"));
    }

    /** Get a tool by name. */
    public static JodyTool getTool(String name) {
        return CORE_TOOLS.stream().filter(t -> t.getName().equals(name)).findFirst().orElse(null);
    }

    /**
     * Convert JodyTool list to LangChain4j ToolSpecification list.
     * Used when building the LLM request.
     */
    public static List<ToolSpecification> toLangChain4jSpecs(List<JodyTool> tools) {
        return tools.stream().map(JodyTool::toSpecification).collect(Collectors.toList());
    }
}
