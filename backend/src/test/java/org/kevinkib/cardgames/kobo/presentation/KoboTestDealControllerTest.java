package org.kevinkib.cardgames.kobo.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.kobo.domain.Kobo;
import org.kevinkib.cardgames.kobo.domain.RandomDeckSource;
import org.kevinkib.cardgames.kobo.domain.ScriptedDeckSource;
import org.kevinkib.cardgames.kobo.presentation.api.KoboFixedDealPayload;
import org.springframework.http.ResponseEntity;
import org.springframework.context.annotation.Profile;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.kevinkib.cardgames.kobo.domain.KoboFixtures.card;

class KoboTestDealControllerTest {

    private final ScriptedDeckSource decks = new ScriptedDeckSource(new RandomDeckSource());
    private final KoboTestDealController controller = new KoboTestDealController(decks);
    private final GameId id = GameId.generate();

    private ResponseEntity<?> put(String gameId, Integer round, String... cards) {
        return controller.fixDeal(gameId, new KoboFixedDealPayload(round, List.of(cards)));
    }

    @Test
    void putDeal_fixesTheRoundAndRepliesNoContent() {
        ResponseEntity<?> response = put(id.uuid().toString(), 1, "KH", "10S", "AD", "2C");

        assertThat(response.getStatusCode().value(), is(204));
        Kobo kobo = new Kobo(id, 2, 1L, decks);
        assertThat(kobo.slots(new PlayerId(0)).stream().map(s -> s.card()).toList(),
                contains(card("KH"), card("10S"), card("AD"), card("2C")));
    }

    @Test
    void putDeal_withoutARound_fixesRoundOne() {
        put(id.uuid().toString(), null, "QH");

        Kobo kobo = new Kobo(id, 2, 1L, decks);
        assertThat(kobo.slots(new PlayerId(0)).get(0).card(), is(card("QH")));
    }

    @Test
    void putDeal_refusesWhatIsNotADeal() {
        String g = id.uuid().toString();
        assertThat(put("nope", 1, "KH").getStatusCode().value(), is(400));
        assertThat(put(g, 1, "KH", "KH").getStatusCode().value(), is(400));
        assertThat(put(g, 1, "1H").getStatusCode().value(), is(400));
        assertThat(put(g, 1, "KX").getStatusCode().value(), is(400));
        assertThat(put(g, 0, "KH").getStatusCode().value(), is(400));
        assertThat(controller.fixDeal(g, null).getStatusCode().value(), is(400));
        assertThat(controller.fixDeal(g, new KoboFixedDealPayload(1, null)).getStatusCode().value(), is(400));
    }

    @Test
    void deleteDeal_forgetsTheFixedCards() {
        put(id.uuid().toString(), 1, "KH", "KD", "KC", "KS");

        assertThat(controller.clearDeal(id.uuid().toString()).getStatusCode().value(), is(204));
        assertThat(controller.clearDeal("nope").getStatusCode().value(), is(400));
        Kobo kobo = new Kobo(id, 2, 1L, decks);
        assertThat(kobo.slots(new PlayerId(0)).stream().map(s -> s.card()).toList()
                .equals(List.of(card("KH"), card("KD"), card("KC"), card("KS"))), is(false));
    }

    @Test
    void theControllerExistsOnlyUnderTheTestProfile() {
        Profile profile = KoboTestDealController.class.getAnnotation(Profile.class);

        assertThat(profile.value(), arrayContaining("test"));
    }
}
