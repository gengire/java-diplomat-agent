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
     * are broadcast to /topic/chat/{sessionCode} and then analyzed by The Diplomat.
     */
    @MessageMapping("/chat/{sessionCode}")
    @SendTo("/topic/chat/{sessionCode}")
    public ChatMessage handleMessage(@DestinationVariable String sessionCode, ChatMessage message) {
        log.info("[{}] {} says: {}", sessionCode, message.getSender(), message.getContent());

        // Persist the message
        conversationService.saveMessage(sessionCode, message.getSender(), message.getContent(), "CHAT");

        // Analyze asynchronously and send Diplomat's response if needed
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
                .content(message.getSender() + " has joined the conversation. Welcome! I'm The Diplomat, your communication helper. I'll be here if you need me. \uD83D\uDC4B")
                .type("JOIN")
                .build();

        return joinNotice;
    }

    /**
     * Handle rewind requests — lets someone rephrase their last message.
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
                .content("\uD83C\uDF21\uFE0F Temperature check! On a scale of 1-10 (1 = calm, 10 = boiling), how are you each feeling right now?")
                .type("TEMPERATURE_CHECK")
                .build();

        conversationService.saveDiplomatMessage(sessionCode, tempNotice.getContent(), "TEMPERATURE_CHECK", null);
        return tempNotice;
    }

    /**
     * Handle "Translate This" requests — The Diplomat rephrases a message to reveal underlying feelings.
     */
    @MessageMapping("/translate/{sessionCode}")
    public void handleTranslate(@DestinationVariable String sessionCode, ChatMessage message) {
        log.info("[{}] {} requested translation of: {}", sessionCode, message.getSender(), message.getContent());

        Thread.startVirtualThread(() -> {
            try {
                // message.content = the text to translate, message.sender = who originally said it
                DiplomatResponse response = diplomatService.translateMessage(
                        sessionCode, message.getSender(), message.getContent());

                conversationService.saveDiplomatMessage(
                        sessionCode, response.getContent(), "TRANSLATION", null);

                ChatMessage diplomatMsg = ChatMessage.builder()
                        .sessionCode(sessionCode)
                        .sender("DIPLOMAT")
                        .content(response.getContent())
                        .type("TRANSLATION")
                        .build();

                messagingTemplate.convertAndSend("/topic/chat/" + sessionCode, diplomatMsg);
            } catch (Exception e) {
                log.error("Translation failed for session {}: {}", sessionCode, e.getMessage());
            }
        });
    }

    /**
     * Handle parking lot — park a topic for later discussion.
     */
    @MessageMapping("/parking-lot/{sessionCode}")
    @SendTo("/topic/chat/{sessionCode}")
    public ChatMessage handleParkingLot(@DestinationVariable String sessionCode, ChatMessage message) {
        log.info("[{}] {} parked topic: {}", sessionCode, message.getSender(), message.getContent());

        ChatMessage notice = ChatMessage.builder()
                .sessionCode(sessionCode)
                .sender("DIPLOMAT")
                .content("\uD83C\uDD7F\uFE0F Parked for later: \"" + message.getContent() + "\" — Great idea to set that aside. You can come back to it when you're ready.")
                .type("PARKING_LOT")
                .build();

        conversationService.saveDiplomatMessage(sessionCode, notice.getContent(), "PARKING_LOT", null);
        return notice;
    }

    /**
     * Handle private messages — a participant sends a private message to The Diplomat for coaching.
     * Messages come in on /app/private/{sessionCode} and responses go to /topic/private/{sessionCode}/{participant}.
     */
    @MessageMapping("/private/{sessionCode}")
    public void handlePrivateMessage(@DestinationVariable String sessionCode, ChatMessage message) {
        log.info("[{}] PRIVATE from {}: {}", sessionCode, message.getSender(), message.getContent());

        // Save the participant's private message
        conversationService.saveMessage(
                sessionCode, message.getSender(), message.getContent(), "PRIVATE", message.getSender());

        // Echo back their own message so it appears in their private panel
        ChatMessage echo = ChatMessage.builder()
                .sessionCode(sessionCode)
                .sender(message.getSender())
                .content(message.getContent())
                .type("PRIVATE")
                .recipient(message.getSender())
                .build();
        messagingTemplate.convertAndSend(
                "/topic/private/" + sessionCode + "/" + message.getSender(), echo);

        // Generate private coaching response in background
        Thread.startVirtualThread(() -> {
            try {
                DiplomatResponse response = diplomatService.respondToPrivateMessage(
                        sessionCode, message.getSender(), message.getContent());

                // Save the diplomat's private response
                conversationService.saveDiplomatMessage(
                        sessionCode, response.getContent(), "PRIVATE_COACHING",
                        null, message.getSender());

                ChatMessage diplomatMsg = ChatMessage.builder()
                        .sessionCode(sessionCode)
                        .sender("DIPLOMAT")
                        .content(response.getContent())
                        .type("PRIVATE_COACHING")
                        .recipient(message.getSender())
                        .build();

                messagingTemplate.convertAndSend(
                        "/topic/private/" + sessionCode + "/" + message.getSender(), diplomatMsg);
            } catch (Exception e) {
                log.error("Private coaching failed for session {} user {}: {}", 
                        sessionCode, message.getSender(), e.getMessage());
            }
        });
    }

    /**
     * Run The Diplomat's analysis in a separate virtual thread so it doesn't block chat message delivery.
     * If the response has a recipient set (private coaching), route to the private channel instead.
     */
    private void analyzeInBackground(String sessionCode, String sender, String content) {
        Thread.startVirtualThread(() -> {
            try {
                DiplomatResponse response = diplomatService.analyzeAndRespond(sessionCode, sender, content);
                if (response != null) {
                    String recipient = response.getRecipient();

                    // Persist the Diplomat's message (with recipient if private)
                    conversationService.saveDiplomatMessage(
                            sessionCode, response.getContent(),
                            response.getResponseType(), response.getFallacyType(),
                            recipient
                    );

                    ChatMessage diplomatMsg = ChatMessage.builder()
                            .sessionCode(sessionCode)
                            .sender("DIPLOMAT")
                            .content(response.getContent())
                            .type(response.getResponseType())
                            .recipient(recipient)
                            .build();

                    if (recipient != null) {
                        // Route to participant's private channel
                        messagingTemplate.convertAndSend(
                                "/topic/private/" + sessionCode + "/" + recipient, diplomatMsg);
                    } else {
                        // Broadcast to the shared chat
                        messagingTemplate.convertAndSend(
                                "/topic/chat/" + sessionCode, diplomatMsg);
                    }
                }
            } catch (Exception e) {
                log.error("Diplomat analysis failed for session {}: {}", sessionCode, e.getMessage());
            }
        });
    }
}
