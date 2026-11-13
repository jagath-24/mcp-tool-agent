package com.jagath.mcpserver.tools;

import jakarta.annotation.PostConstruct;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Basic keyword search over a small set of local documents (src/main/resources/docs).
 * Swap this out for a real vector/full-text index later — this is intentionally
 * simple so the agent loop and tool-trace plumbing can be demoed end to end.
 */
@Component
public class DocumentTool {

    private final Map<String, String> documents = new LinkedHashMap<>();

    @PostConstruct
    void loadDocuments() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:docs/*.txt");
            for (Resource resource : resources) {
                StringBuilder content = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        content.append(line).append(" ");
                    }
                }
                documents.put(resource.getFilename(), content.toString().trim());
            }
        } catch (Exception e) {
            // Non-fatal: tool will just report zero documents loaded.
        }
    }

    @Tool(name = "search_documents",
          description = "Searches locally stored text documents for a keyword or short "
                  + "phrase and returns matching filenames with a short snippet of context.")
    public String searchDocuments(
            @ToolParam(description = "Keyword or phrase to search for") String keyword) {

        if (keyword == null || keyword.isBlank()) {
            return "Provide a non-empty keyword to search for.";
        }
        String needle = keyword.toLowerCase();
        List<String> matches = new ArrayList<>();

        for (Map.Entry<String, String> doc : documents.entrySet()) {
            String haystack = doc.getValue().toLowerCase();
            int idx = haystack.indexOf(needle);
            if (idx >= 0) {
                int start = Math.max(0, idx - 40);
                int end = Math.min(doc.getValue().length(), idx + needle.length() + 40);
                String snippet = doc.getValue().substring(start, end);
                matches.add(doc.getKey() + ": ..." + snippet + "...");
            }
        }

        if (matches.isEmpty()) {
            return "No documents matched \"" + keyword + "\".";
        }
        return String.join("\n", matches);
    }
}
