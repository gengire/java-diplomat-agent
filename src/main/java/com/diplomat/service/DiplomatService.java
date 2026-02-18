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
     * May return a response with a recipient set for private coaching.
     */
    public DiplomatResponse analyzeAndRespond(String sessionCode, String sender, String newMessage) {
        Conversation conv = conversationService.findBySessionCode(sessionCode)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        // Use the higher of the two interaction levels (if either person wants help, they get it)
        int effectiveLevel = Math.max(conv.getInteractionLevelA(), conv.getInteractionLevelB());

        // Build context
        List<Message> recentMessages = conversationService.getRecentMessages(sessionCode, CONTEXT_WINDOW);
        String conversationHistory = formatConversationHistory(recentMessages);
        String constitutionText = getConstitutionText(conv);
        String systemPrompt = loadSystemPrompt();

        // Build the full prompt
        String fullPrompt = buildAnalysisPrompt(
                systemPrompt, constitutionText, conversationHistory,
                conv.getParticipantA(), conv.getParticipantB(),
                sender, newMessage, conv.getMode(), effectiveLevel
        );

        log.debug("Sending analysis prompt to LLM for session {}", sessionCode);

        try {
            String response = chatModel.generate(fullPrompt);
            return parseResponse(response, conv.getParticipantA(), conv.getParticipantB());
        } catch (Exception e) {
            log.error("LLM call failed for session {}: {}", sessionCode, e.getMessage());
            return null;
        }
    }

    /**
     * Respond to a private coaching message from a participant.
     * The Diplomat acts as a personal coach, giving advice privately.
     */
    public DiplomatResponse respondToPrivateMessage(String sessionCode, String participant, String message) {
        Conversation conv = conversationService.findBySessionCode(sessionCode)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        // Get recent conversation context (public + this user's private messages)
        List<Message> recentMessages = conversationService.getRecentMessagesForParticipant(
                sessionCode, participant, CONTEXT_WINDOW);
        String conversationHistory = formatConversationHistory(recentMessages);
        String constitutionText = getConstitutionText(conv);

        String otherParticipant = participant.equals(conv.getParticipantA())
                ? conv.getParticipantB() : conv.getParticipantA();

        String prompt = """
                You are The Diplomat — a private communication coach. %s has sent you a PRIVATE message
                that the other participant (%s) cannot see.
                
                You are now in 1-on-1 coaching mode. Be warm, direct, and helpful.
                
                In this private channel you can:
                - Help them understand their own feelings and reactions
                - Suggest better ways to phrase what they want to say
                - Help them see their partner's perspective
                - Give them specific scripts or phrases to try
                - Validate their feelings while challenging unhelpful patterns
                - Help them prepare what to say before saying it in the shared chat
                - Be more candid than you would be publicly
                
                === CONSTITUTION ===
                %s
                
                === RECENT CONVERSATION (includes shared + private) ===
                %s
                
                === %s's PRIVATE MESSAGE TO YOU ===
                %s
                
                Respond directly, warmly, and helpfully. Keep it conversational — you're their coach, not a textbook.
                Be brief (2-4 sentences) unless they're asking for something more detailed.
                Do NOT use bracket formatting. Just respond naturally.
                """.formatted(participant, otherParticipant, constitutionText,
                conversationHistory, participant, message);

        try {
            String response = chatModel.generate(prompt);
            return DiplomatResponse.builder()
                    .sender(DIPLOMAT_SENDER)
                    .content(response)
                    .responseType("PRIVATE_COACHING")
                    .recipient(participant)
                    .timestamp(LocalDateTime.now())
                    .build();
        } catch (Exception e) {
            log.error("Private coaching failed for {}: {}", participant, e.getMessage());
            return DiplomatResponse.builder()
                    .sender(DIPLOMAT_SENDER)
                    .content("Sorry, I'm having trouble responding right now. Try again in a moment.")
                    .responseType("PRIVATE_COACHING")
                    .recipient(participant)
                    .timestamp(LocalDateTime.now())
                    .build();
        }
    }

    /**
     * Generate a conversation debrief/summary.
     */
    public DiplomatResponse generateDebrief(String sessionCode) {
        List<Message> allMessages = conversationService.getAllMessages(sessionCode);
        String history = formatConversationHistory(allMessages);

        String prompt = """
                You are The Diplomat, a communication mediator. Provide a brief, constructive debrief of this conversation.
                
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
                for how they communicate during difficult conversations. You are The Diplomat.
                
                Be directive and proactive — guide them toward best practices. If the current constitution
                is missing important elements, proactively suggest additions. Make it feel collaborative,
                not imposed.
                
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
                                        String sender, String newMessage, String mode, int interactionLevel) {
        String levelGuidance = switch (interactionLevel) {
            case 1, 2 -> "INTERACTION LEVEL: MINIMAL (" + interactionLevel + "/10). Stay almost completely silent. Only intervene for serious fallacies or personal attacks. Let them work it out.";
            case 3, 4 -> "INTERACTION LEVEL: LOW (" + interactionLevel + "/10). Intervene sparingly — only for clear fallacies, constitution violations, or sharp escalation. No reframes or observations unless critical.";
            case 5, 6 -> "INTERACTION LEVEL: BALANCED (" + interactionLevel + "/10). Intervene when genuinely helpful — fallacies, escalation, good reframing opportunities. Don't comment on every message.";
            case 7, 8 -> "INTERACTION LEVEL: ACTIVE (" + interactionLevel + "/10). Be more engaged — offer reflections ('What I heard you say is...'), reframes, encouragement. Actively facilitate the discussion. Call out smaller issues too.";
            case 9, 10 -> "INTERACTION LEVEL: VERY DIRECTIVE (" + interactionLevel + "/10). Actively mediate like a counselor. Summarize each person's points. Ask clarifying questions. Guide the conversation structure. Suggest next topics. Offer 'What I Heard' reflections frequently.";
            default -> "INTERACTION LEVEL: BALANCED (5/10).";
        };

        return """
                %s
                
                === CONSTITUTION (agreed upon rules) ===
                %s
                
                === PARTICIPANTS ===
                Person A: %s
                Person B: %s
                
                === CONVERSATION MODE ===
                %s
                
                === %s ===
                
                === RECENT CONVERSATION ===
                %s
                
                === NEW MESSAGE ===
                %s: %s
                
                === YOUR TASK ===
                Analyze the new message in context. Decide if you should intervene.
                
                If you should intervene, respond with EXACTLY this format:
                [TYPE: OBSERVATION|REFRAME|FALLACY_ALERT|TEMPERATURE_CHECK|CONSTITUTION_REMINDER|REFLECTION|APPRECIATION_PROMPT]
                [FALLACY: name_of_fallacy or NONE]
                [VISIBILITY: PUBLIC or PRIVATE_TO_%s or PRIVATE_TO_%s]
                [RESPONSE: your message to the participants]
                
                VISIBILITY guidance:
                - Use PUBLIC for most interventions (both people should see it)
                - Use PRIVATE_TO_name when you want to privately coach just one person:
                  * Suggesting a better way to phrase something BEFORE they say it
                  * Pointing out their own pattern without embarrassing them
                  * Offering encouragement or validation privately
                  * Giving them a heads-up about how their message might land
                
                If no intervention is needed, respond with exactly:
                [NO_INTERVENTION]
                
                Intervene when you see:
                - Logical fallacies (ad hominem, straw man, whataboutism, false equivalence, hasty generalization, etc.)
                - Escalation or rising tension
                - Constitution rule violations
                - Statements that could be reframed more constructively
                - One person dominating or the other withdrawing
                - Opportunities for positive reinforcement
                - Moments where summarizing what someone said would help ("What I heard you say is...")
                
                Adjust your intervention frequency based on the interaction level above.
                In FREE_TALK mode, lean toward observing. In GUIDED mode, actively facilitate and structure.
                Be warm, brief, and non-judgmental. Never take sides. You are The Diplomat.
                """.formatted(systemPrompt, constitution, participantA, participantB,
                mode, levelGuidance, history, sender, newMessage, participantA, participantB);
    }

    private DiplomatResponse parseResponse(String raw, String participantA, String participantB) {
        if (raw == null || raw.contains("[NO_INTERVENTION]")) {
            return null;
        }

        String type = extractBracketValue(raw, "TYPE");
        String fallacy = extractBracketValue(raw, "FALLACY");
        String visibility = extractBracketValue(raw, "VISIBILITY");
        String response = extractBracketValue(raw, "RESPONSE");

        if (response == null || response.isBlank()) {
            // Try to use the whole response if parsing failed
            response = raw.replaceAll("\\[TYPE:.*?\\]", "")
                          .replaceAll("\\[FALLACY:.*?\\]", "")
                          .replaceAll("\\[VISIBILITY:.*?\\]", "")
                          .replaceAll("\\[RESPONSE:.*?\\]", "")
                          .trim();
            if (response.isBlank()) response = raw;
        }

        if ("NONE".equalsIgnoreCase(fallacy)) fallacy = null;
        if (type == null) type = "OBSERVATION";

        // Determine recipient from visibility
        String recipient = null;
        if (visibility != null) {
            String vis = visibility.trim().toUpperCase();
            if (vis.startsWith("PRIVATE_TO_")) {
                String targetName = visibility.trim().substring("PRIVATE_TO_".length()).trim();
                // Match against participant names (case-insensitive)
                if (targetName.equalsIgnoreCase(participantA)) {
                    recipient = participantA;
                } else if (targetName.equalsIgnoreCase(participantB)) {
                    recipient = participantB;
                }
            }
        }

        return DiplomatResponse.builder()
                .sender(DIPLOMAT_SENDER)
                .content(response)
                .responseType(type.trim())
                .fallacyType(fallacy)
                .recipient(recipient)
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
                You are directive about establishing good communication practices and the constitution.
                """;
    }

    /**
     * Translate a message from one person into what they likely meant underneath.
     */
    public DiplomatResponse translateMessage(String sessionCode, String originalSender, String messageContent) {
        String prompt = """
                You are The Diplomat, a relationship translator. Reframe this statement to reveal the underlying
                feeling and need, without losing the speaker's intent.
                
                %s said: "%s"
                
                Provide a brief, warm translation that:
                1. Removes blame language
                2. Expresses the underlying feeling ("I feel...")
                3. States the underlying need ("I need...")
                4. Keeps it natural and conversational
                
                Respond with ONLY the translated version, like:
                "What [name] might be trying to say is: ..."
                """.formatted(originalSender, messageContent);

        try {
            String response = chatModel.generate(prompt);
            return DiplomatResponse.builder()
                    .sender(DIPLOMAT_SENDER)
                    .content(response)
                    .responseType("TRANSLATION")
                    .timestamp(LocalDateTime.now())
                    .build();
        } catch (Exception e) {
            log.error("Translation failed: {}", e.getMessage());
            return DiplomatResponse.builder()
                    .sender(DIPLOMAT_SENDER)
                    .content("Sorry, I couldn't translate that right now.")
                    .responseType("TRANSLATION")
                    .timestamp(LocalDateTime.now())
                    .build();
        }
    }
}
