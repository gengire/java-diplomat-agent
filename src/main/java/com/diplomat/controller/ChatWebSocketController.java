package com.diplomat.controller;

import com.diplomat.dto.ChatMessage;
import com.diplomat.dto.DiplomatResponse;
import com.diplomat.service.ConversationService;
import com.diplomat.service.DiplomatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatWebSocketController {

    private final ConversationService conversationService;
    private final DiplomatService diplomatService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Handle incoming chat messages. Messages sent to /app/chat/{sessionCode}
     * are broadcast to /topic/chat/{sessionCode} and then analyzed by the Diplomat.
     */
    @MessageMapping("/chat/{sessionCode}")
    @SendTo("/topic/chat/{sessionCode}")
    public ChatMessage handleMessage(@DestinationVariable String sessionCode, ChatMessage message) {
        log.info("[{}] {} says: {}", sessionCode, message.getSender(), message.getContent());

        // Persist the message
        conversationService.saveMessage(sessionCode, message.getSender(), message.getContent(), "CHAT");

        // Analyze asynchronously and send diplomat response if needed
        analyzeInBackground(sessionCode, message.getSender(), message.getContent());

        return message;
    }

    /**
     * Handle join events.
     */
    @MessageMapping("/join/{sessionCode}")
    @SendTo("/topic/chat/{sessionCode}")
    public ChatMessage handleJoin(@DestinationVariable String sessionCode, ChatMessage message) {
        log.info("[{}] {} joined the conversation", sessionCode, message.getSender());

        ChatMessage joinNotice = ChatMessage.builder()
                .sessionCode(sessionCode)
                .sender("SYSTEM")
                .content(message.getSender() + " has joined the conversation.")
                .type("JOIN")
                .build();

        return joinNotice;
    }

    /**
     * Handle rewind requests.
     */
    @MessageMapping("/rewind/{sessionCode}")
    @SendTo("/topic/chat/{sessionCode}")
    public ChatMessage handleRewind(@DestinationVariable String sessionCode, ChatMessage message) {
        log.info("[{}] {} requested a rewind", sessionCode, message.getSender());

        ChatMessage rewindNotice = ChatMessage.builder()
                .sessionCode(sessionCode)
                .sender("SYSTEM")
                .content(message.getSender() + " would like to rewind. " + message.getSender() + ", go ahead and rephrase what you meant.")
                .type("REWIND")
                .build();

        conversationService.saveMessage(sessionCode, "SYSTEM", rewindNotice.getContent(), "SYSTEM");
        return rewindNotice;
    }

    /**
     * Handle temperature check requests.
     */
    @MessageMapping("/tempcheck/{sessionCode}")
    @SendTo("/topic/chat/{sessionCode}")
    public ChatMessage handleTempCheck(@DestinationVariable String sessionCode, ChatMessage message) {
        ChatMessage tempNotice = ChatMessage.builder()
                .sessionCode(sessionCode)
                .sender("DIPLOMAT")
                .content("🌡️ Temperature check! On a scale of 1-10 (1 = calm, 10 = boiling), how are you each feeling right now?")
                .type("TEMPERATURE_CHECK")
                .build();

        conversationService.saveDiplomatMessage(sessionCode, tempNotice.getContent(), "TEMPERATURE_CHECK", null);
        return tempNotice;
    }

    /**
     * Run diplomat analysis in a separate thread so it doesn't block the chat message delivery.
     */
    private void analyzeInBackground(String sessionCode, String sender, String content) {
        Thread.startVirtualThread(() -> {
            try {
                DiplomatResponse response = diplomatService.analyzeAndRespond(sessionCode, sender, content);
                if (response != null) {
                    // Persist the diplomat's message
                    conversationService.saveDiplomatMessage(
                            sessionCode, response.getContent(),
                            response.getResponseType(), response.getFallacyType()
                    );

                    // Broadcast to the chat
                    ChatMessage diplomatMsg = ChatMessage.builder()
                            .sessionCode(sessionCode)
                            .sender("DIPLOMAT")
                            .content(response.getContent())
                            .type(response.getResponseType())
                            .build();

                    messagingTemplate.convertAndSend("/topic/chat/" + sessionCode, diplomatMsg);
                }
            } catch (Exception e) {
                log.error("Diplomat analysis failed for session {}: {}", sessionCode, e.getMessage());
            }
        });
    }
}
