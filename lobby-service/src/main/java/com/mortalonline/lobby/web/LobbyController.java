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

    /** Salida EXPLICITA de la sala (libera el cupo de inmediato). */
    @PostMapping("/rooms/{id}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@PathVariable Long id, Principal principal) {
        lobby.removePlayer(id, Long.valueOf(principal.getName()));
    }

    /** Borra la sala (solo el creador). */
    @DeleteMapping("/rooms/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Principal principal) {
        lobby.deleteRoom(id, Long.valueOf(principal.getName()));
    }
}
