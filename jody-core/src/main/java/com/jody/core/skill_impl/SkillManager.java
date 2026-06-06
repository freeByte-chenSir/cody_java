package com.jody.core.skill_impl;

import com.jody.core.config.Config;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Skill manager implementing the Agent Skills open standard.
 *
 *
 * Two-layer priority:
 *   1. Managed skills (bundled with Jody)
 *   2. User/project skills (custom dirs + project .jody/skills/)
 *
 * Each skill is a directory containing a SKILL.md file with YAML frontmatter.
 */
public class SkillManager {

    private static final Pattern FRONTMATTER_PATTERN = Pattern.compile(
            "^---\s*\n(.*?)\n---\s*\n(.*)", Pattern.DOTALL);

    private final List<Path> skillDirs = new ArrayList<>();
    private final Map<String, SkillDef> skills = new LinkedHashMap<>();
    private boolean loaded;

    public SkillManager(Config config, Path workdir) {
        // Built-in managed skills dir (bundled with jar)
        Path managedDir = Path.of(System.getProperty("jody.skills.dir",
                Path.of(System.getProperty("user.home"), ".jody", "skills").toString()));
        if (Files.isDirectory(managedDir)) {
            skillDirs.add(managedDir);
        }

        // Custom skill dirs from config
        for (String dir : config.getSkills().getCustomDirs()) {
            Path p = Path.of(dir);
            if (Files.isDirectory(p)) {
                skillDirs.add(p);
            }
        }

        // Project .jody/skills/
        if (workdir != null) {
            Path projectSkills = workdir.resolve(".jody").resolve("skills");
            if (Files.isDirectory(projectSkills)) {
                skillDirs.add(projectSkills);
            }
        }
    }

    /** Load all skills from all configured directories. */
    public synchronized void load() {
        if (loaded) return;
        for (Path dir : skillDirs) {
            try (Stream<Path> walk = Files.walk(dir, 2)) {
                walk.filter(p -> p.getFileName().toString().equals("SKILL.md"))
                        .forEach(this::loadSkill);
            } catch (IOException ignored) {}
        }
        loaded = true;
    }

    private void loadSkill(Path skillMd) {
        try {
            String content = Files.readString(skillMd);
            Matcher m = FRONTMATTER_PATTERN.matcher(content);
            if (!m.find()) return;

            String yaml = m.group(1);
            String body = m.group(2);

            SkillDef skill = new SkillDef();
            skill.name = extractYamlField(yaml, "name");
            skill.description = extractYamlField(yaml, "description");
            skill.version = extractYamlField(yaml, "version");
            skill.author = extractYamlField(yaml, "author");
            String tagsStr = extractYamlField(yaml, "tags");
            if (tagsStr != null) {
                skill.tags = Arrays.asList(tagsStr.split(",\s*"));
            }
            skill.body = body;
            skill.path = skillMd.getParent();

            if (skill.name != null && !skill.name.isEmpty()) {
                skills.put(skill.name, skill);
            }
        } catch (IOException ignored) {}
    }

    private String extractYamlField(String yaml, String field) {
        Pattern p = Pattern.compile("^" + field + ":\s*(.+)$", Pattern.MULTILINE);
        Matcher m = p.matcher(yaml);
        if (m.find()) {
            return m.group(1).trim().replaceAll("^[\"']|[\"']$", "");
        }
        return null;
    }

    /** List all loaded skill names. */
    public List<String> listSkills() {
        load();
        return new ArrayList<>(skills.keySet());
    }

    /** List skill details with description. */
    public List<Map<String, Object>> listSkillDetails() {
        load();
        return skills.values().stream()
                .map(s -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", s.name);
                    m.put("description", s.description);
                    m.put("version", s.version);
                    return m;
                })
                .collect(Collectors.toList());
    }

    /** Read a skill's full content by name. */
    public String readSkill(String name) {
        load();
        SkillDef skill = skills.get(name);
        if (skill == null) return null;
        return "---\nname: " + skill.name + "\ndescription: " + skill.description + "\n---\n\n" + skill.body;
    }

    /** Build skills XML for system prompt inclusion. */
    public String buildSkillsXml() {
        load();
        if (skills.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("<skills>\n");
        for (SkillDef s : skills.values()) {
            sb.append("  <skill name=\"").append(escapeXml(s.name)).append("\">\n");
            sb.append("    <description>").append(escapeXml(s.description)).append("</description>\n");
            sb.append("  </skill>\n");
        }
        sb.append("</skills>");
        return sb.toString();
    }

    private String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    // ── Inner Types ──────────────────────────────────────────────────

    public static class SkillDef {
        public String name;
        public String description;
        public String version;
        public String author;
        public List<String> tags = new ArrayList<>();
        public String body;
        public Path path;
    }
}
