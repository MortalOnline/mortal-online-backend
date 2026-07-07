package com.mortalonline.combat.web;

import com.mortalonline.combat.match.MatchService;
import com.mortalonline.combat.repository.MatchRepository;
import com.mortalonline.combat.repository.MatchRoundRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

@RestController
@RequestMapping("/combat")
public class CombatController {

    private final MatchService matchService;
    private final MatchRepository matches;
    private final MatchRoundRepository rounds;

    public CombatController(MatchService matchService, MatchRepository matches, MatchRoundRepository rounds) {
        this.matchService = matchService;
        this.matches = matches;
        this.rounds = rounds;
    }

    /** Registra la partida en memoria al empezar el combate (sin tocar DB). */
    @PostMapping("/matches")
    @ResponseStatus(HttpStatus.CREATED)
    public Dtos.ActiveMatchView start(@RequestBody Dtos.StartMatchRequest req, Principal principal) {
        var m = matchService.start(req.roomId(), req.player1Id(), req.player2Id(),
                Long.valueOf(principal.getName()));
        return new Dtos.ActiveMatchView(m.roomId, m.player1Id, m.player2Id, m.startedAt);
    }

    /** Detalle de una partida FINALIZADA (persistida). */
    @GetMapping("/matches/{id}")
    public Dtos.MatchView match(@PathVariable Long id) {
        var match = matches.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Partida no encontrada"));
        return Dtos.MatchView.of(match, rounds.findByMatchIdOrderByRoundNumber(id));
    }
}
