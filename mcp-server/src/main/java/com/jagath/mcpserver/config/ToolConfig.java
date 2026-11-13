package com.jagath.mcpserver.config;

import com.jagath.mcpserver.tools.DatabaseTool;
import com.jagath.mcpserver.tools.DocumentTool;
import com.jagath.mcpserver.tools.WeatherTool;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the @Tool-annotated methods with Spring AI's MCP server
 * auto-configuration. The MCP server starter picks up any ToolCallbackProvider
 * bean automatically and exposes those tools over tools/list and tools/call.
 */
@Configuration
public class ToolConfig {

    @Bean
    public ToolCallbackProvider mcpTools(DatabaseTool databaseTool,
                                          DocumentTool documentTool,
                                          WeatherTool weatherTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(databaseTool, documentTool, weatherTool)
                .build();
    }
}
