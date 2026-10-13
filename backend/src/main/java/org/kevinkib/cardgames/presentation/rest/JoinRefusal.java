package org.kevinkib.cardgames.presentation.rest;

import org.kevinkib.cardgames.presentation.dto.JoinResponseDto;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameAlreadyStartedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * The 409 of a refused join. The reason travels in the {@value #HEADER} header ("full" or "started") so a client
 * can tell the player which one it was; the body stays empty.
 */
public final class JoinRefusal {

    public static final String HEADER = "X-Join-Refusal";

    private JoinRefusal() {
    }

    public static ResponseEntity<JoinResponseDto> conflict(RuntimeException refusal) {
        String reason = refusal instanceof GameAlreadyStartedException ? "started" : "full";
        return ResponseEntity.status(HttpStatus.CONFLICT).header(HEADER, reason).build();
    }
}
