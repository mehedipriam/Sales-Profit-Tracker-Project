package com.salestracker.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The order agent. For now only a test endpoint that reads a pasted message; it saves nothing. */
@RestController
@RequestMapping("/api/agent")
public class AgentController {
    public record ExtractRequest(@NotBlank @Size(max = 4000) String text) {}

    private final AiClient ai;

    public AgentController(AiClient ai) {
        this.ai = ai;
    }

    @PostMapping("/extract")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ExtractedOrder extract(@Valid @RequestBody ExtractRequest req) {
        return ai.extractOrder(req.text());
    }
}
