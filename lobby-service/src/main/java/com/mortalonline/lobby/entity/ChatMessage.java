package com.mortalonline.lobby.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "chat_messages", indexes = @Index(columnList = "roomId, sentAt"))
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long roomId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(nullable = false, updatable = false)
    private Instant sentAt = Instant.now();

    protected ChatMessage() {
    }

    public ChatMessage(Long roomId, Long userId, String content) {
        this.roomId = roomId;
        this.userId = userId;
        this.content = content;
    }

    public Long getId() { return id; }
    public Long getRoomId() { return roomId; }
    public Long getUserId() { return userId; }
    public String getContent() { return content; }
    public Instant getSentAt() { return sentAt; }
}
