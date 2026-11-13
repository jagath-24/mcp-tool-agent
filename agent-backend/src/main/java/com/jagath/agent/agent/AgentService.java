package com.jagath.agent.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jagath.agent.dto.AskResponse;
import com.jagath.agent.dto.ToolCallTrace;
import com.jagath.agent.mcp.McpToolClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The agent loop: ask the LLM what to do, execute any tool calls it asks for
 * via the MCP client, feed results back, repeat until it gives a final answer
 * or we hit the iteration cap (guards against infinite tool-call loops).
 */
@Service
public class AgentService {

    private static final String SYSTEM_PROMPT = """
            You are a helpful assistant with access to tools: query_database
            (read-only SQL against the app's MySQL database), search_documents
            (keyword search over local docs), and get_weather (current weather
            by city). Use a tool whenever the question needs data you don't
            already have. You may call more than one tool, one after another,
            if the question needs it. Once you have enough information, answer
            the user directly in plain language — don't describe the tool
            calls, just answer.
            """;

    private final GroqClient groqClient;
    private final McpToolClient mcpToolClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${groq.max-tool-iterations:5}")
    private int maxIterations;

    public AgentService(GroqClient groqClient, McpToolClient mcpToolClient) {
        this.groqClient = groqClient;
        this.mcpToolClient = mcpToolClient;
    }

    public AskResponse ask(String question) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        messages.add(Map.of("role", "user", "content", question));

        List<Map<String, Object>> tools = buildToolDefinitions();
        List<ToolCallTrace> trace = new ArrayList<>();

        for (int iteration = 1; iteration <= maxIterations; iteration++) {
            Map<String, Object> response = groqClient.chat(messages, tools);
            Map<String, Object> message = extractMessage(response);
            messages.add(message);

            List<Map<String, Object>> toolCalls = extractToolCalls(message);

            if (toolCalls.isEmpty()) {
                String answer = (String) message.getOrDefault("content", "(no answer returned)");
                return new AskResponse(answer, trace);
            }

            for (Map<String, Object> toolCall : toolCalls) {
                trace.add(executeToolCall(toolCall, messages, trace.size() + 1));
            }
        }

        return new AskResponse(
                "Reached the tool-call limit (" + maxIterations + ") without a final answer. "
                        + "Check the trace below for what was tried.",
                trace);
    }

    public List<Map<String, String>> listAvailableTools() {
        List<Map<String, String>> result = new ArrayList<>();
        for (McpSchema.Tool tool : mcpToolClient.listTools()) {
            result.add(Map.of("name", tool.name(), "description",
                    tool.description() == null ? "" : tool.description()));
        }
        return result;
    }

    private List<Map<String, Object>> buildToolDefinitions() {
        List<Map<String, Object>> tools = new ArrayList<>();
        for (McpSchema.Tool tool : mcpToolClient.listTools()) {
            tools.add(Map.of(
                    "type", "function",
                    "function", Map.of(
                            "name", tool.name(),
                            "description", tool.description() == null ? "" : tool.description(),
                            "parameters", mcpToolClient.schemaAsMap(tool)
                    )
            ));
        }
        return tools;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMessage(Map<String, Object> response) {
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("Groq response had no choices: " + response);
        }
        return (Map<String, Object>) choices.get(0).get("message");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractToolCalls(Map<String, Object> message) {
        Object raw = message.get("tool_calls");
        if (raw == null) {
            return List.of();
        }
        return (List<Map<String, Object>>) raw;
    }

    @SuppressWarnings("unchecked")
    private ToolCallTrace executeToolCall(Map<String, Object> toolCall,
                                           List<Map<String, Object>> messages,
                                           int step) {
        String callId = (String) toolCall.get("id");
        Map<String, Object> function = (Map<String, Object>) toolCall.get("function");
        String toolName = (String) function.get("name");
        String argsJson = (String) function.getOrDefault("arguments", "{}");

        Map<String, Object> args;
        try {
            args = argsJson.isBlank() ? Map.of() : objectMapper.readValue(argsJson, Map.class);
        } catch (Exception e) {
            args = Map.of();
        }

        String output = mcpToolClient.callTool(toolName, args);

        messages.add(Map.of(
                "role", "tool",
                "tool_call_id", callId,
                "content", output
        ));

        return new ToolCallTrace(step, toolName, args, output);
    }
}
