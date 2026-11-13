package com.jagath.agent.web;

import com.jagath.agent.agent.AgentService;
import com.jagath.agent.dto.AskRequest;
import com.jagath.agent.dto.AskResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/agent/ask")
    public AskResponse ask(@RequestBody AskRequest request) {
        if (request.question() == null || request.question().isBlank()) {
            return new AskResponse("Ask me something first.", List.of());
        }
        return agentService.ask(request.question());
    }

    @GetMapping("/tools")
    public List<Map<String, String>> listTools() {
        return agentService.listAvailableTools();
    }
}
