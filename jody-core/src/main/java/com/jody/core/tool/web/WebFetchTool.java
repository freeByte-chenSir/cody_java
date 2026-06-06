package com.jody.core.tool.web;

import com.jody.core.tool.JodyTool;
import com.jody.core.tool.ToolContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fetches a URL and returns the content.
 *
 *
 * In a full implementation this would convert HTML to markdown using a library
 * like Jsoup. For now it returns raw HTML or error.
 */
public class WebFetchTool implements JodyTool {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override public String getName() { return "webfetch"; }

    @Override public String getDescription() {
        return "Fetch content from a URL and convert HTML to readable text.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("url", Map.of("type", "string", "description", "The URL to fetch"));
        return props;
    }

    @Override public List<String> getRequiredParameters() { return List.of("url"); }

    @Override
    public String execute(ToolContext ctx, Map<String, Object> arguments) {
        String url = (String) arguments.get("url");

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Jody-Agent/2.0")
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();

            if (status >= 400) {
                return "[HTTP_ERROR] " + status + " when fetching " + url;
            }

            String body = response.body();
            if (body == null || body.isEmpty()) {
                return "[EMPTY_PAGE] No content returned from " + url;
            }

            // Truncate very large responses
            if (body.length() > 100_000) {
                body = body.substring(0, 100_000) + "\n[TRUNCATED: response too large ("
                        + body.length() + " total chars)]";
            }

            // Simple HTML-to-text: strip tags (a full impl would use Jsoup)
            String text = body.replaceAll("<[^>]+>", " ")
                    .replaceAll("\\s+", " ")
                    .trim();
            return text;
        } catch (Exception e) {
            return "[FETCH_ERROR] Failed to fetch " + url + ": " + e.getMessage();
        }
    }
}
