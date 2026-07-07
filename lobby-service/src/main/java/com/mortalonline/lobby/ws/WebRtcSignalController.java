package com.mortalonline.lobby.ws;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

/**
 * Senalizacion WebRTC (HU-06, voz): reenvia ofertas SDP, respuestas SDP y
 * candidatos ICE entre los dos peers de la sala SIN interpretarlos (relay
 * puro). El audio viaja peer-to-peer por WebRTC y NUNCA pasa por el backend.
 *
 * El cliente publica en /app/rooms/{id}/webrtc-signal y ambos peers escuchan
 * /topic/rooms/{id}/webrtc-signal; cada peer descarta los mensajes cuyo
 * senderId sea el propio.
 */
@Controller
public class WebRtcSignalController {

    private final SimpMessagingTemplate broker;

    public WebRtcSignalController(SimpMessagingTemplate broker) {
        this.broker = broker;
    }

    @MessageMapping("/rooms/{roomId}/webrtc-signal")
    public void relay(@DestinationVariable Long roomId, Map<String, Object> signal, Principal principal) {
        if (signal == null) return;
        Map<String, Object> out = new HashMap<>(signal); // payload opaco: no se interpreta
        out.put("senderId", Long.valueOf(principal.getName()));
        broker.convertAndSend("/topic/rooms/" + roomId + "/webrtc-signal", out);
    }
}
