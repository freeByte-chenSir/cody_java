package com.jody.core.tool.web;

import com.jody.core.tool.JodyTool;
import com.jody.core.tool.ToolContext;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Searches the web using DuckDuckGo HTML (non-JS) results.
 *
 */
public class WebSearchTool implements JodyTool {

    private static final Pattern RESULT_PATTERN =
            Pattern.compile("<a[^>]*class=\"result__a\"[^>]*href=\"([^\"]+)\"[^>]*>([^<]+)</a>");
    private static final Pattern SNIPPET_PATTERN =
            Pattern.compile("<a[^>]*class=\"result__snippet\"[^>]*>(.*?)</a>",
                    Pattern.DOTALL);

    @Override public String getName() { return "websearch"; }

    @Override public String getDescription() {
        return "Search the web using DuckDuckGo and return formatted results.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("query", Map.of("type", "string", "description", "The search query"));
        return props;
    }

    @Override public List<String> getRequiredParameters() { return List.of("query"); }

    @Override
    public String execute(ToolContext ctx, Map<String, Object> arguments) {
        String query = (String) arguments.get("query");

        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String searchUrl = "https://html.duckduckgo.com/html/?q=" + encodedQuery;

            URI uri = URI.create(searchUrl);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Jody-Agent/2.0");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(10_000);

            int status = conn.getResponseCode();
            if (status != 200) {
                return "[SEARCH_ERROR] DuckDuckGo returned HTTP " + status + " for query: " + query;
            }

            StringBuilder html = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    html.append(line).append("\n");
                }
            }

            String body = html.toString();

            // Extract results using regex
            StringBuilder results = new StringBuilder();
            results.append("Search results for: \"").append(query).append("\"\n\n");

            Matcher linkMatcher = RESULT_PATTERN.matcher(body);
            int count = 0;
            while (linkMatcher.find() && count < 10) {
                count++;
                String url = linkMatcher.group(1);
                String title = linkMatcher.group(2).trim()
                        .replaceAll("<[^>]+>", "")
                        .replaceAll("&amp;", "&")
                        .replaceAll("&lt;", "<")
                        .replaceAll("&gt;", ">");
                results.append(count).append(". ").append(title).append("\n");
                results.append("   ").append(url).append("\n\n");
            }

            if (count == 0) {
                return "[NO_RESULTS] No search results found for: " + query;
            }

            return results.toString().trim();
        } catch (Exception e) {
            return "[SEARCH_ERROR] Failed to search for '" + query + "': " + e.getMessage();
        }
    }
}
