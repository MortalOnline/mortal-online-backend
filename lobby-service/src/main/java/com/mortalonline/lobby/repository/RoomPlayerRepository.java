package com.mortalonline.lobby.repository;

import com.mortalonline.lobby.entity.RoomPlayer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoomPlayerRepository extends JpaRepository<RoomPlayer, Long> {
    List<RoomPlayer> findByRoomId(Long roomId);
    long countByRoomId(Long roomId);
    Optional<RoomPlayer> findFirstByUserIdOrderByJoinedAtDesc(Long userId);
    boolean existsByRoomIdAndUserId(Long roomId, Long userId);
    void deleteByRoomIdAndUserId(Long roomId, Long userId);
}
