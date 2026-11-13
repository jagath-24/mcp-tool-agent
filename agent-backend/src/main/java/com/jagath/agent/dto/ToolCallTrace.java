package com.jagath.agent.dto;

/**
 * One recorded tool invocation, shown step-by-step on the frontend.
 */
public record ToolCallTrace(int step, String tool, Object input, String output) {
}
