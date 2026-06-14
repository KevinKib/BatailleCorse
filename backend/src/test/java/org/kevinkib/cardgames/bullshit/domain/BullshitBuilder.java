package org.kevinkib.cardgames.bullshit.domain;

import org.kevinkib.cardgames.bullshit.domain.claim.AscendingRankClaimMode;
import org.kevinkib.cardgames.bullshit.domain.claim.ClaimMode;
import org.kevinkib.cardgames.bullshit.domain.claim.ClaimTarget;
import org.kevinkib.cardgames.bullshit.domain.claim.RankTarget;
import org.kevinkib.cardgames.bullshit.domain.player.Player;
import org.kevinkib.cards.domain.deck.french.FrenchRank;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class BullshitBuilder {

    private BullshitId id = BullshitId.generate();
    private List<Player> players = new ArrayList<>();
    private ClaimMode claimMode = new AscendingRankClaimMode();
    private ClaimTarget currentTarget = null;
    private int currentPlayerIndex = 0;

    public static BullshitBuilder aBullshit() {
        return new BullshitBuilder();
    }

    public BullshitBuilder withPlayers(Player... players) {
        this.players = new ArrayList<>(Arrays.asList(players));
        return this;
    }

    public BullshitBuilder withPlayers(List<Player> players) {
        this.players = new ArrayList<>(players);
        return this;
    }

    public BullshitBuilder withClaimMode(ClaimMode claimMode) {
        this.claimMode = claimMode;
        return this;
    }

    public BullshitBuilder withCurrentTarget(FrenchRank rank) {
        this.currentTarget = new RankTarget(rank);
        return this;
    }

    public BullshitBuilder withCurrentPlayerIndex(int index) {
        this.currentPlayerIndex = index;
        return this;
    }

    public Bullshit build() {
        ClaimTarget target = currentTarget != null ? currentTarget : claimMode.initial();
        return new Bullshit(id, players, claimMode, target, currentPlayerIndex);
    }
}
