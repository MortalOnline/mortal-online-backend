package com.mortalonline.lobby.web;

import com.mortalonline.lobby.service.LobbyService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/lobby")
public class LobbyController {

    private final LobbyService lobby;

    public LobbyController(LobbyService lobby) {
        this.lobby = lobby;
    }

    /** Lista de salas activas con su estado (WAITING / IN_GAME). */
    @GetMapping("/rooms")
    public List<Dtos.RoomView> rooms() {
        return lobby.listActiveRooms();
    }

    @PostMapping("/rooms")
    @ResponseStatus(HttpStatus.CREATED)
    public Dtos.RoomView create(@RequestBody Dtos.CreateRoomRequest req, Principal principal) {
        return lobby.createRoom(Long.valueOf(principal.getName()), req);
    }

    @PostMapping("/rooms/{id}/join")
    public Dtos.RoomView join(@PathVariable Long id, Principal principal) {
        return lobby.join(id, Long.valueOf(principal.getName()));
    }
}
