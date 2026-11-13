package com.jagath.agent.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Thin wrapper around the official MCP Java SDK's synchronous client.
 * Connects once at startup to the local mcp-server over SSE and exposes
 * simple listTools()/callTool() methods for the agent loop to use.
 */
@Component
public class McpToolClient {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private McpSyncClient client;

    @Value("${mcp.server.url}")
    private String mcpServerUrl;

    @PostConstruct
    void connect() {
        HttpClientSseClientTransport transport = HttpClientSseClientTransport
                .builder(mcpServerUrl)
                .build();

        client = McpClient.sync(transport)
                .clientInfo(new McpSchema.Implementation("agent-backend", "1.0.0"))
                .build();

        client.initialize();
    }

    @PreDestroy
    void disconnect() {
        if (client != null) {
            client.closeGracefully();
        }
    }

    public List<McpSchema.Tool> listTools() {
        return client.listTools().tools();
    }

    /**
     * Calls a tool by name with a JSON-arguments map and returns the result
     * flattened to plain text — MCP tool results are a list of content blocks,
     * most commonly a single text block for the tools this project defines.
     */
    public String callTool(String toolName, Map<String, Object> arguments) {
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(toolName, arguments);
        McpSchema.CallToolResult result = client.callTool(request);

        StringBuilder text = new StringBuilder();
        for (McpSchema.Content content : result.content()) {
            if (content instanceof McpSchema.TextContent textContent) {
                text.append(textContent.text());
            } else {
                try {
                    text.append(objectMapper.writeValueAsString(content));
                } catch (Exception e) {
                    text.append(content);
                }
            }
        }

        String output = text.toString();
        if (Boolean.TRUE.equals(result.isError())) {
            output = "Tool reported an error: " + output;
        }
        return output;
    }

    /**
     * Converts an MCP tool's JSON input schema into the plain Map shape the
     * Groq (OpenAI-compatible) "tools" request field expects, regardless of
     * whether the SDK version returns the schema as a Map or a typed record.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> schemaAsMap(McpSchema.Tool tool) {
        Object schema = tool.inputSchema();
        if (schema instanceof Map) {
            return (Map<String, Object>) schema;
        }
        return objectMapper.convertValue(schema, Map.class);
    }
}
