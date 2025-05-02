package org.kevinkib.bataillecorse;

import org.kevinkib.bataillecorse.domain.BatailleCorse;
import org.kevinkib.bataillecorse.domain.Player;

public class BatailleCorseController {

    private BatailleCorse batailleCorse;

    public BatailleCorseController() {

    }

    public void startGame(Integer nbPlayers) {
        batailleCorse = new BatailleCorse(nbPlayers);
    }

    public void send(int playerIndex) {
        Player player = batailleCorse.getPlayerByIndex(playerIndex);
        try {
            batailleCorse.send(player);
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

}
