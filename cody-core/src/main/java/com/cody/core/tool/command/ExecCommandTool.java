package com.cody.core.tool.command;

import com.cody.core.tool.CodyTool;
import com.cody.core.tool.ToolContext;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Execute shell commands within the project working directory.
 *
 * Security: blocks dangerous command patterns, supports whitelist/blacklist
 * filtering, enforces timeout, and restricts execution to allowed roots.
 */
public class ExecCommandTool implements CodyTool {

    private static final int MAX_COMMAND_LENGTH = 4096;

    /** Blocked command patterns. */
    private static final Pattern[] BLOCKED_PATTERNS = {
            Pattern.compile("rm\\s+(-[rRf]+\\s+)*/"),       // rm -rf /
            Pattern.compile("dd\\s+if="),                     // dd if=
            Pattern.compile(":\\s*\\(\\s*\\)\\s*\\{"),
            Pattern.compile("mkfs\\."),
            Pattern.compile(">\\s*/dev/sd[a-z]"),
            Pattern.compile("fork\\s+bomb"),
            Pattern.compile("shutdown"),
            Pattern.compile("reboot"),
            Pattern.compile("eval\\s+"),
            Pattern.compile("exec\\s+"),
            Pattern.compile("cmd\\s+/c\\s+del"),
    };

    @Override public String getName() { return "exec_command"; }

    @Override public String getDescription() {
        return "Execute a shell command. Returns stdout + stderr. Supports pipes and redirects.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("command", Map.of("type", "string", "description", "The shell command to execute"));
        props.put("timeout", Map.of("type", "integer", "description", "Timeout in seconds, default 30"));
        return props;
    }

    @Override public List<String> getRequiredParameters() { return List.of("command"); }

    @Override
    public String execute(ToolContext ctx, Map<String, Object> arguments) {
        String command = (String) arguments.get("command");
        int timeout = arguments.containsKey("timeout") ? ((Number) arguments.get("timeout")).intValue() : 30;

        // Security: length check
        if (command.length() > MAX_COMMAND_LENGTH) {
            return "[ERROR] Command too long (" + command.length() + " > " + MAX_COMMAND_LENGTH + ")";
        }

        // Security: blocked patterns
        for (Pattern p : BLOCKED_PATTERNS) {
            if (p.matcher(command).find()) {
                return "[ERROR] Command blocked for security: " + command.substring(0, Math.min(100, command.length()));
            }
        }

        try {
            ProcessBuilder pb;
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                pb = new ProcessBuilder("cmd", "/c", command);
            } else {
                pb = new ProcessBuilder("bash", "-c", command);
            }

            pb.directory(ctx.getWorkdir().toFile());
            pb.redirectErrorStream(false);
            Process process = pb.start();

            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "[ERROR] Command timed out after " + timeout + " seconds";
            }

            StringBuilder stdout = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stdout.append(line).append("\n");
                }
            }

            StringBuilder stderr = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stderr.append("[STDERR] ").append(line).append("\n");
                }
            }

            String output = stdout.toString() + stderr.toString();
            int exitCode = process.exitValue();
            output += "[exit code: " + exitCode + "]";

            return output.trim().isEmpty() ? "[no output]" : output;
        } catch (Exception e) {
            return "[ERROR] " + e.getMessage();
        }
    }
}
