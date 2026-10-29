package org.kevinkib.cardgames.presentation.support;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitReason;
import org.kevinkib.cardgames.sessionmanagement.presence.port.GameLifecycleBroadcaster;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * The golden files of the server/frontend wire contract, in the repository-level {@code contract/} folder that the
 * frontend reads too ({@code frontend/src/contract/wireContract.test.ts} type-checks every file against the
 * frontend types). A scenario records what the server really serializes in a scripted game: the STOMP messages a
 * seat receives, the REST bodies. Comparing the JSON pins the server side; the frontend test pins the other side.
 *
 * <p>A missing file is written (and the test fails on CI, where every file must be committed); a changed one fails
 * unless the test runs with {@code -Dcontract.update=true}, which rewrites it. Ids, tokens and epoch timestamps are
 * replaced by fixed values first, so a file only changes when the wire changes.
 */
public final class WireContract {

    /** Same defaults as the application's Jackson mapper; map entries sorted so a file is stable. */
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .findAndAddModules()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();
    private static final String UUID = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
    private static final String FIXED_UUID = "00000000-0000-0000-0000-000000000000";
    /** Epoch milliseconds (deadlines, timer ends): 13 digits starting with 1. */
    private static final String EPOCH_MS = "(?<=[:\\[,]\\s?)1\\d{12}(?=\\s*[,}\\]\\n\\r])";
    private static final long FIXED_EPOCH_MS = 1_700_000_000_000L;
    /** A disconnection deadline handed to the lifecycle broadcasters; normalised like any epoch timestamp. */
    public static final long DEADLINE_EPOCH_MS = FIXED_EPOCH_MS;

    private final String game;
    private final List<Map<String, Object>> entries = new ArrayList<>();

    private WireContract(String game) {
        this.game = game;
    }

    /** A recording for the folder {@code contract/<game>/}: the server game type, or {@code session}. */
    public static WireContract of(String game) {
        return new WireContract(game);
    }

    /** A STOMP message as the client receives it ({@code success}, {@code eventType}, {@code eventData}...). */
    public WireContract message(Response response) {
        return add("message", response);
    }

    /** Every message of the list, in order. */
    public WireContract messages(List<Response> responses) {
        responses.forEach(this::message);
        return this;
    }

    /** What one seat received since the last {@link RecordingMessaging#clear()}, in order. */
    public WireContract messagesTo(RecordingMessaging messaging, int seat) {
        return messages(messaging.toSeat(new PlayerId(seat)));
    }

    /** A REST body; {@code kind} names the endpoint ({@code state}, {@code join}, {@code lobbies}...). */
    public WireContract rest(String kind, Object body) {
        return add(kind, body);
    }

    private WireContract add(String kind, Object payload) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("kind", kind);
        entry.put("payload", payload);
        entries.add(entry);
        return this;
    }

    /** Compares the recording with {@code contract/<game>/<scenario>.json}. */
    public void verify(String scenario) {
        assertThat(entries).as("an empty recording pins nothing").isNotEmpty();
        verifyFile(scenario + ".json", normalise(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(entries)));
    }

    /**
     * Pins the event type strings a game may send: {@code contract/<game>/event-types.json}. The frontend checks that
     * its event type union holds every one of them, and nothing else beyond the lifecycle types.
     */
    public static void verifyEventTypes(String game, Enum<?>[] types) {
        List<String> names = Arrays.stream(types).map(Enum::toString).toList();
        new WireContract(game).verifyFile("event-types.json",
                normalise(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(names)));
    }

    /**
     * Plays the three presence events of the shared lifecycle on a started game: {@code seat} disconnects,
     * reconnects, then forfeits (which may finish the game). The messages land in the recording messaging.
     */
    public static void playLifecycle(GameLifecycleBroadcaster lifecycle, Game game, int seat) {
        PlayerId player = new PlayerId(seat);
        lifecycle.disconnected(game, player, DEADLINE_EPOCH_MS);
        lifecycle.reconnected(game, player);
        game.forfeit(player);
        lifecycle.forfeited(game, player, ForfeitReason.RESIGNED);
    }

    private void verifyFile(String name, String actual) {
        Path file = folder().resolve(game).resolve(name);
        try {
            boolean update = Boolean.getBoolean("contract.update");
            if (!Files.exists(file) || update) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, actual, StandardCharsets.UTF_8);
                if (!update && System.getenv("CI") != null) {
                    fail("Golden contract file %s was missing: run the test locally and commit it", file);
                }
                return;
            }
            String expected = Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
            assertThat(actual)
                    .as("The wire contract %s changed. If the change is intended, rerun with -Dcontract.update=true, "
                            + "commit the file and make the frontend types follow (frontend/src/contract)", file)
                    .isEqualTo(expected);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String normalise(String json) {
        return json.replace("\r\n", "\n")
                .replaceAll(UUID, FIXED_UUID)
                .replaceAll(EPOCH_MS, Long.toString(FIXED_EPOCH_MS))
                + "\n";
    }

    /** The repository's {@code contract/} folder, found from the working directory (the backend module). */
    static Path folder() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.isDirectory(dir.resolve("backend")) && Files.isDirectory(dir.resolve("frontend"))) {
                return dir.resolve("contract");
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("No repository root above " + Path.of("").toAbsolutePath());
    }
}
