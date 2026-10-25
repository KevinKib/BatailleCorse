package org.kevinkib.cardgames.presentation;

/** STOMP destinations of the public game list. They are open to everybody: the list carries no secret. */
public final class PublicLobbyTopics {

    private static final String PREFIX = "/topic/lobbies/";

    private PublicLobbyTopics() {
    }

    /** The topic that carries the whole public list of one game type. */
    public static String destination(String gameType) {
        return PREFIX + gameType;
    }
}
