package com.mortalonline.lobby.ws;

import com.mortalonline.lobby.entity.ChatMessage;
import com.mortalonline.lobby.repository.ChatMessageRepository;
import com.mortalonline.lobby.web.Dtos;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * Chat de sala (HU-05): el cliente publica en /app/rooms/{id}/chat y todos
 * los miembros lo reciben por /topic/rooms/{id}/chat. Se persiste para
 * historial.
 */
@Controller
public class ChatSocketController {

    private final ChatMessageRepository messages;
    private final SimpMessagingTemplate broker;

    public ChatSocketController(ChatMessageRepository messages, SimpMessagingTemplate broker) {
        this.messages = messages;
        this.broker = broker;
    }

    @MessageMapping("/rooms/{roomId}/chat")
    public void chat(@DestinationVariable Long roomId, Dtos.ChatIn payload, Principal principal) {
        if (payload == null || payload.content() == null || payload.content().isBlank()) return;
        String content = payload.content().length() > 500 ? payload.content().substring(0, 500) : payload.content();

        Long userId = Long.valueOf(principal.getName());
        ChatMessage saved = messages.save(new ChatMessage(roomId, userId, content));

        String username = principal instanceof StompPrincipal sp ? sp.username() : principal.getName();
        broker.convertAndSend("/topic/rooms/" + roomId + "/chat",
                new Dtos.ChatOut(roomId, userId, username, saved.getContent(), saved.getSentAt()));
    }
}
