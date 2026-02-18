package com.diplomat.service;

import com.diplomat.dto.DiplomatResponse;
import com.diplomat.model.Constitution;
import com.diplomat.model.Conversation;
import com.diplomat.model.Message;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The core brain of the Diplomat Agent. Analyzes conversations and decides
 * when and how to intervene.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DiplomatService {

    private final ChatLanguageModel chatModel;
    private final ConversationService conversationService;
    private final ConstitutionService constitutionService;

    private static final int CONTEXT_WINDOW = 30; // last N messages for context
    private static final String DIPLOMAT_SENDER = "DIPLOMAT";

    /**
     * Analyze the latest message in context and decide whether to intervene.
     * Returns null if no intervention is needed.
     */
    public DiplomatResponse analyzeAndRespond(String sessionCode, String sender, String newMessage) {
        Conversation conv = conversationService.findBySessionCode(sessionCode)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        // Build context
        List<Message> recentMessages = conversationService.getRecentMessages(sessionCode, CONTEXT_WINDOW);
        String conversationHistory = formatConversationHistory(recentMessages);
        String constitutionText = getConstitutionText(conv);
        String systemPrompt = loadSystemPrompt();

        // Build the full prompt
        String fullPrompt = buildAnalysisPrompt(
                systemPrompt, constitutionText, conversationHistory,
                conv.getParticipantA(), conv.getParticipantB(),
                sender, newMessage, conv.getMode()
        );

        log.debug("Sending analysis prompt to LLM for session {}", sessionCode);

        try {
            String response = chatModel.generate(fullPrompt);
            return parseResponse(response);
        } catch (Exception e) {
            log.error("LLM call failed for session {}: {}", sessionCode, e.getMessage());
            return null;
        }
    }

    /**
     * Generate a conversation debrief/summary.
     */
    public DiplomatResponse generateDebrief(String sessionCode) {
        List<Message> allMessages = conversationService.getAllMessages(sessionCode);
        String history = formatConversationHistory(allMessages);

        String prompt = """
                You are the Diplomat, a communication mediator. Provide a brief, constructive debrief of this conversation.
                
                Include:
                1. What went well — positive communication moments
                2. Patterns observed — recurring themes or friction points
                3. Fallacies detected — any logical fallacies that appeared
                4. Suggestions — concrete tips for next time
                
                Keep it balanced, kind, and actionable. Don't take sides.
                
                Conversation:
                %s
                """.formatted(history);

        try {
            String response = chatModel.generate(prompt);
            return DiplomatResponse.builder()
                    .sender(DIPLOMAT_SENDER)
                    .content(response)
                    .responseType("SUMMARY")
                    .timestamp(LocalDateTime.now())
                    .build();
        } catch (Exception e) {
            log.error("Failed to generate debrief: {}", e.getMessage());
            return DiplomatResponse.builder()
                    .sender(DIPLOMAT_SENDER)
                    .content("I wasn't able to generate a debrief at this time.")
                    .responseType("SUMMARY")
                    .timestamp(LocalDateTime.now())
                    .build();
        }
    }

    /**
     * Help refine the constitution with AI suggestions.
     */
    public String suggestConstitutionImprovement(String currentConstitution, String request) {
        String prompt = """
                You are helping a couple create their Communication Constitution — a set of agreed-upon rules
                for how they communicate during difficult conversations.
                
                Current constitution:
                %s
                
                Their request: %s
                
                Provide an updated version of the constitution incorporating their request.
                Keep it clear, fair, and balanced. Use Markdown formatting.
                Only output the updated constitution text, nothing else.
                """.formatted(currentConstitution, request);

        return chatModel.generate(prompt);
    }

    // --- Private helpers ---

    private String buildAnalysisPrompt(String systemPrompt, String constitution,
                                        String history, String participantA, String participantB,
                                        String sender, String newMessage, String mode) {
        return """
                %s
                
                === CONSTITUTION (agreed upon rules) ===
                %s
                
                === PARTICIPANTS ===
                Person A: %s
                Person B: %s
                
                === CONVERSATION MODE ===
                %s
                
                === RECENT CONVERSATION ===
                %s
                
                === NEW MESSAGE ===
                %s: %s
                
                === YOUR TASK ===
                Analyze the new message in context. Decide if you should intervene.
                
                If you should intervene, respond with EXACTLY this format:
                [TYPE: OBSERVATION|REFRAME|FALLACY_ALERT|TEMPERATURE_CHECK|CONSTITUTION_REMINDER]
                [FALLACY: name_of_fallacy or NONE]
                [RESPONSE: your message to the participants]
                
                If no intervention is needed, respond with exactly:
                [NO_INTERVENTION]
                
                Intervene when you see:
                - Logical fallacies (ad hominem, straw man, whataboutism, false equivalence, hasty generalization, etc.)
                - Escalation or rising tension
                - Constitution rule violations
                - Statements that could be reframed more constructively
                - One person dominating or the other withdrawing
                
                Do NOT intervene on every message. Only when it genuinely helps.
                In FREE_TALK mode, intervene less. In GUIDED mode, facilitate more actively.
                Be warm, brief, and non-judgmental. Never take sides.
                """.formatted(systemPrompt, constitution, participantA, participantB,
                mode, history, sender, newMessage);
    }

    private DiplomatResponse parseResponse(String raw) {
        if (raw == null || raw.contains("[NO_INTERVENTION]")) {
            return null;
        }

        String type = extractBracketValue(raw, "TYPE");
        String fallacy = extractBracketValue(raw, "FALLACY");
        String response = extractBracketValue(raw, "RESPONSE");

        if (response == null || response.isBlank()) {
            // Try to use the whole response if parsing failed
            response = raw.replaceAll("\\[TYPE:.*?\\]", "")
                          .replaceAll("\\[FALLACY:.*?\\]", "")
                          .replaceAll("\\[RESPONSE:.*?\\]", "")
                          .trim();
            if (response.isBlank()) response = raw;
        }

        if ("NONE".equalsIgnoreCase(fallacy)) fallacy = null;
        if (type == null) type = "OBSERVATION";

        return DiplomatResponse.builder()
                .sender(DIPLOMAT_SENDER)
                .content(response)
                .responseType(type.trim())
                .fallacyType(fallacy)
                .timestamp(LocalDateTime.now())
                .build();
    }

    private String extractBracketValue(String text, String key) {
        String pattern = "\\[" + key + ":\\s*(.+?)\\]";
        var matcher = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.DOTALL).matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private String formatConversationHistory(List<Message> messages) {
        if (messages.isEmpty()) return "(conversation just started)";
        return messages.stream()
                .map(m -> "%s [%s]: %s".formatted(m.getSender(), m.getMessageType(), m.getContent()))
                .collect(Collectors.joining("\n"));
    }

    private String getConstitutionText(Conversation conv) {
        if (conv.getConstitution() != null) {
            return conv.getConstitution().getContent();
        }
        return "(No constitution set for this session — using general best practices)";
    }

    private String loadSystemPrompt() {
        try {
            ClassPathResource resource = new ClassPathResource("prompts/diplomat-system.txt");
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Could not load system prompt file, using default");
            return getDefaultSystemPrompt();
        }
    }

    private String getDefaultSystemPrompt() {
        return """
                You are The Diplomat — an AI communication mediator helping two people have more productive conversations.
                You are warm, neutral, and insightful. You never take sides.
                Your job is to observe, translate, and gently intervene when communication breaks down.
                """;
    }
}
