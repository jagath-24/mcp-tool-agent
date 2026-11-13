package com.jagath.agent.dto;

import java.util.List;

public record AskResponse(String answer, List<ToolCallTrace> toolCalls) {
}
