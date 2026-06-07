package com.jody.cli;

import com.jody.core.runner.AgentRunner;
import com.jody.core.runner.StreamEvent;
import com.jody.sdk.Jody;
import com.jody.sdk.JodyClient;
import com.jody.cli.render.ConsoleRenderer;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.concurrent.Callable;

/**
 * CLI entry point*
 * Commands: run, chat, config
 *
 * Usage:
 *   jody run "Create a hello world program"
 *   jody run --model claude-sonnet-4-0 "Explain this code"
 *   jody chat
 *   jody config show
 */
@Command(name = "jody", mixinStandardHelpOptions = true, version = "2.0.0",
         description = "AI Coding Agent CLI")
public class Main implements Callable<Integer> {

    @Option(names = {"-m", "--model"}, description = "Model name")
    private String model;

    @Option(names = {"-k", "--api-key"}, description = "API key")
    private String apiKey;

    @Option(names = {"--base-url"}, description = "Model base URL")
    private String baseUrl;

    @Option(names = {"--thinking"}, description = "Enable thinking mode")
    private Boolean thinking;

    @Option(names = {"-w", "--workdir"}, description = "Working directory", defaultValue = ".")
    private Path workdir;

    @Option(names = {"--auto-approve"}, description = "Auto-approve all tool calls")
    private boolean autoApprove;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main())
                .addSubcommand("run", new RunCommand())
                .addSubcommand("chat", new ChatCommand())
                .addSubcommand("config", new ConfigCommand())
                .execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        // Default: enter interactive chat (like `claude`)
        return new CommandLine(new ChatCommand()).execute();
    }

    // ── Run Subcommand ──────────────────────────────────────────────

    @Command(name = "run", description = "Run an AI agent task")
    static class RunCommand implements Callable<Integer> {

        @Parameters(description = "The task prompt")
        private String prompt;

        @Option(names = {"-m", "--model"}, description = "Model name")
        private String model;

        @Option(names = {"-k", "--api-key"}, description = "API key")
        private String apiKey;

        @Option(names = {"--base-url"}, description = "Model base URL")
        private String baseUrl;

        @Option(names = {"--thinking"}, description = "Enable thinking mode")
        private Boolean thinking;

        @Option(names = {"-w", "--workdir"}, description = "Working directory", defaultValue = ".")
        private Path workdir;

        @Option(names = {"--auto-approve"}, description = "Auto-approve tools")
        private boolean autoApprove;

        @Option(names = {"--stream"}, description = "Stream output")
        private boolean stream;

        @Override
        public Integer call() {
            try {
                Jody.JodyBuilder builder = Jody.builder().workdir(workdir).autoApprove(autoApprove);
                if (model != null) builder.model(model);
                if (apiKey != null) builder.apiKey(apiKey);
                if (baseUrl != null) builder.baseUrl(baseUrl);
                if (thinking != null) builder.enableThinking(thinking);

                JodyClient client = builder.build();

                if (stream) {
                    ConsoleRenderer renderer = new ConsoleRenderer();
                    Iterator<StreamEvent> events = client.stream(prompt);
                    while (events.hasNext()) {
                        StreamEvent ev = events.next();
                        if (ev == StreamEvent.POISON_PILL) break;
                        renderer.render(ev);
                    }
                    renderer.flush();
                } else {
                    AgentRunner.RunResult result = client.run(prompt);
                    System.out.println(result.getOutput());
                }
                return 0;
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
        }
    }

    // ── Chat Subcommand ──────────────────────────────────────────────

    @Command(name = "chat", description = "Interactive chat mode")
    static class ChatCommand implements Callable<Integer> {

        @Option(names = {"-w", "--workdir"}, description = "Working directory", defaultValue = ".")
        private Path workdir;

        @Override
        public Integer call() {
            System.out.println("Chat mode (press Ctrl+C to exit)");
            System.out.println("(Interactive chat is a simplified version; use run for single tasks)");

            JodyClient client = Jody.builder().workdir(workdir).autoApprove(false).build();

            try (java.util.Scanner scanner = new java.util.Scanner(System.in)) {
                while (true) {
                    System.out.print("\n> ");
                    if (!scanner.hasNextLine()) break;
                    String input = scanner.nextLine().trim();
                    if (input.isEmpty()) continue;
                    if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("quit")) break;

                    AgentRunner.RunResult result = client.run(input);
                    System.out.println(result.getOutput());
                }
            }
            return 0;
        }
    }

    // ── Config Subcommand ────────────────────────────────────────────

    @Command(name = "config", description = "View or edit configuration")
    static class ConfigCommand implements Callable<Integer> {

        @Parameters(arity = "0..1", description = "Action: show (default)")
        private String action = "show";

        @Override
        public Integer call() {
            if ("show".equals(action)) {
                Path configPath = Path.of(System.getProperty("user.home"), ".jody", "config.json");
                if (java.nio.file.Files.exists(configPath)) {
                    try {
                        System.out.println(java.nio.file.Files.readString(configPath));
                    } catch (java.io.IOException e) {
                        System.err.println("Error reading config: " + e.getMessage());
                        return 1;
                    }
                } else {
                    System.out.println("No config file found at " + configPath);
                    System.out.println("Create one to customize Jody settings.");
                }
            }
            return 0;
        }
    }
}
