package org.kevinkib.cardgames.bullshit.presentation.dto.event;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.presentation.dto.event.EventData;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

class BullshitEventTypeTest {

    @Test
    void eventTypeStringsAreStable() {
        assertThat(BullshitEventType.DISCARD.toString(), is("DISCARD"));
        assertThat(BullshitEventType.CALL_BULLSHIT.toString(), is("CALL_BULLSHIT"));
    }

    @Test
    void eventDataImplementsMarker() {
        assertThat(new DiscardEventData(0, "ACE", 1), instanceOf(EventData.class));
        assertThat(new CallBullshitEventData(1, 0, false, 0, List.of()), instanceOf(EventData.class));
        assertThat(new BullshitCreateEventData("id", "bullshit", java.util.Map.of()), instanceOf(EventData.class));
    }
}
