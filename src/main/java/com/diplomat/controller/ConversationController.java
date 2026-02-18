package com.diplomat.controller;

import com.diplomat.dto.DiplomatResponse;
import com.diplomat.dto.JoinRequest;
import com.diplomat.model.Conversation;
import com.diplomat.model.Message;
import com.diplomat.service.ConversationService;
import com.diplomat.service.DiplomatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;
    private final DiplomatService diplomatService;

    /**
     * Create a new conversation session.
     */
    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> createSession(@RequestBody JoinRequest request) {
        Conversation conv = conversationService.createSession(request.getParticipantName());
        return ResponseEntity.ok(Map.of(
                "sessionCode", conv.getSessionCode(),
                "participantA", conv.getParticipantA(),
                "status", conv.getStatus()
        ));
    }

    /**
     * Join an existing session.
     */
    @PostMapping("/join")
    public ResponseEntity<Map<String, Object>> joinSession(@RequestBody JoinRequest request) {
        Conversation conv = conversationService.joinSession(request.getSessionCode(), request.getParticipantName());
        return ResponseEntity.ok(Map.of(
                "sessionCode", conv.getSessionCode(),
                "participantA", conv.getParticipantA(),
                "participantB", conv.getParticipantB(),
                "status", conv.getStatus()
        ));
    }

    /**
     * Get session info.
     */
    @GetMapping("/{sessionCode}")
    public ResponseEntity<Map<String, Object>> getSession(@PathVariable String sessionCode) {
        Conversation conv = conversationService.findBySessionCode(sessionCode)
                .orElseThrow(() -> new RuntimeException("Session not found"));
        return ResponseEntity.ok(Map.of(
                "sessionCode", conv.getSessionCode(),
                "participantA", conv.getParticipantA(),
                "participantB", conv.getParticipantB() != null ? conv.getParticipantB() : "",
                "status", conv.getStatus(),
                "mode", conv.getMode(),
                "interactionLevelA", conv.getInteractionLevelA(),
                "interactionLevelB", conv.getInteractionLevelB()
        ));
    }

    /**
     * Get message history.
     */
    @GetMapping("/{sessionCode}/messages")
    public ResponseEntity<List<Message>> getMessages(@PathVariable String sessionCode) {
        return ResponseEntity.ok(conversationService.getAllMessages(sessionCode));
    }

    /**
     * Change conversation mode.
     */
    @PostMapping("/{sessionCode}/mode")
    public ResponseEntity<Map<String, String>> setMode(@PathVariable String sessionCode, @RequestBody Map<String, String> body) {
        String mode = body.get("mode");
        conversationService.setMode(sessionCode, mode);
        return ResponseEntity.ok(Map.of("mode", mode));
    }

    /**
     * Request a debrief summary.
     */
    @PostMapping("/{sessionCode}/debrief")
    public ResponseEntity<DiplomatResponse> debrief(@PathVariable String sessionCode) {
        DiplomatResponse response = diplomatService.generateDebrief(sessionCode);
        conversationService.saveDiplomatMessage(sessionCode, response.getContent(), "SUMMARY", null);
        return ResponseEntity.ok(response);
    }

    /**
     * Set interaction level for a participant.
     */
    @PostMapping("/{sessionCode}/interaction-level")
    public ResponseEntity<Map<String, Object>> setInteractionLevel(
            @PathVariable String sessionCode, @RequestBody Map<String, Object> body) {
        String participant = (String) body.get("participant");
        int level = ((Number) body.get("level")).intValue();
        Conversation conv = conversationService.setInteractionLevel(sessionCode, participant, level);
        return ResponseEntity.ok(Map.of(
                "participant", participant,
                "level", level,
                "interactionLevelA", conv.getInteractionLevelA(),
                "interactionLevelB", conv.getInteractionLevelB()
        ));
    }

    /**
     * End a session.
     */
    @PostMapping("/{sessionCode}/end")
    public ResponseEntity<Map<String, String>> endSession(@PathVariable String sessionCode) {
        conversationService.endSession(sessionCode);
        return ResponseEntity.ok(Map.of("status", "ENDED"));
    }
}
