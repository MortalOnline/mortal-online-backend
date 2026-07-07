package com.mortalonline.lobby.repository;

import com.mortalonline.lobby.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoomRepository extends JpaRepository<Room, Long> {
    List<Room> findByStatusInOrderByCreatedAtDesc(List<Room.Status> statuses);
}
