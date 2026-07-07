package com.mortalonline.combat.match;

import com.mortalonline.combat.entity.Match;
import com.mortalonline.combat.entity.MatchRound;
import com.mortalonline.combat.repository.MatchRepository;
import com.mortalonline.combat.repository.MatchRoundRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

/**
 * Ciclo de vida de la partida. El estado en curso vive en ActiveMatchRegistry
 * (memoria); aqui solo se persiste el resultado FINAL y se notifica a
 * Progress Service (REST sincrono).
 *
 * SEGURIDAD: el cliente calcula el resultado solo para MOSTRAR; el que vale
 * es el que se registra aqui, y solo puede reportarlo un jugador de la
 * partida autenticado por JWT.
 */
@Service
public class MatchService {

    private final ActiveMatchRegistry registry;
    private final MatchRepository matches;
    private final MatchRoundRepository rounds;
    private final ProgressClient progress;

    public MatchService(ActiveMatchRegistry registry, MatchRepository matches,
                        MatchRoundRepository rounds, ProgressClient progress) {
        this.registry = registry;
        this.matches = matches;
        this.rounds = rounds;
        this.progress = progress;
    }

    /** Inicia la partida EN MEMORIA (sin tocar base de datos). */
    public ActiveMatchRegistry.ActiveMatch start(Long roomId, Long player1Id, Long player2Id, Long requesterId) {
        if (roomId == null || player1Id == null || player2Id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "roomId, player1Id y player2Id son obligatorios");
        }
        if (!requesterId.equals(player1Id) && !requesterId.equals(player2Id)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo un jugador de la partida puede iniciarla");
        }
        return registry.start(roomId, player1Id, player2Id);
    }

    /** Registra el fin de una ronda EN MEMORIA (flujo caliente, sin DB). */
    public void roundEnded(Long roomId, Long reporterId, int roundNumber, Long winnerId, int durationSeconds) {
        ActiveMatchRegistry.ActiveMatch match = requireMatch(roomId, reporterId, winnerId);
        match.rounds.add(new ActiveMatchRegistry.RoundResult(roundNumber, winnerId, durationSeconds));
    }

    /**
     * Fin de partida: AHORA si se persiste (Match + MatchRound) y se notifica
     * a Progress Service para scoreboard/racha/desbloqueo.
     */
    @Transactional
    public Match matchEnded(Long roomId, Long reporterId, Long winnerId) {
        ActiveMatchRegistry.ActiveMatch active = requireMatch(roomId, reporterId, winnerId);
        registry.remove(roomId);

        int wonP1 = (int) active.rounds.stream().filter(r -> r.winnerId().equals(active.player1Id)).count();
        int wonP2 = (int) active.rounds.stream().filter(r -> r.winnerId().equals(active.player2Id)).count();
        Match saved = matches.save(new Match(roomId, active.player1Id, active.player2Id, winnerId,
                wonP1, wonP2, active.startedAt, Instant.now()));
        for (ActiveMatchRegistry.RoundResult r : active.rounds) {
            rounds.save(new MatchRound(saved.getId(), r.roundNumber(), r.winnerId(), r.durationSeconds()));
        }

        Long loserId = winnerId.equals(active.player1Id) ? active.player2Id : active.player1Id;
        progress.notifyMatchCompleted(saved.getId(), winnerId, loserId);
        return saved;
    }

    private ActiveMatchRegistry.ActiveMatch requireMatch(Long roomId, Long reporterId, Long winnerId) {
        ActiveMatchRegistry.ActiveMatch match = registry.get(roomId);
        if (match == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay partida activa en esa sala");
        }
        if (!match.hasPlayer(reporterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No perteneces a esta partida");
        }
        if (winnerId == null || !match.hasPlayer(winnerId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El ganador no es un jugador de la partida");
        }
        return match;
    }
}
