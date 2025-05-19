package org.kevinkib.bataillecorse.presentation.websocket.v1;

import org.kevinkib.bataillecorse.domain.BatailleCorse;
import org.kevinkib.bataillecorse.domain.Player;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import org.springframework.web.util.HtmlUtils;

@Controller
public class BatailleCorseWebSocketController {

    private BatailleCorse batailleCorse;

    public BatailleCorseWebSocketController() {
        System.out.println("bbb");
    }

    @MessageMapping("/chat")
    public boolean startGame(Integer nbPlayers) {
        batailleCorse = new BatailleCorse(nbPlayers);
        return true;
    }

    @MessageMapping("/connect")
    @SendTo("/topic/greetings")
    public Greeting greeting(HelloMessage message) throws Exception {
        return new Greeting("Hello, " + HtmlUtils.htmlEscape(message.getName()) + "!");
    }

    @MessageMapping("/hello")             // correspond à "/app/hello"
    @SendTo("/topic/greetings")          // envoie à "/topic/greetings"
    public String handleMessage(String message) {
        System.out.println("Message reçu : " + message);
        return "Réponse du serveur : " + message;
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
