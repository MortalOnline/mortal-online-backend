package com.mortalonline.lobby.repository;

import com.mortalonline.lobby.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findTop50ByRoomIdOrderBySentAtDesc(Long roomId);
}
