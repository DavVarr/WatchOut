package beans;

import p2p.P2PServiceOuterClass;
import player.AbstractPlayer;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
/**
 * This class represents player data that needs to be stored in various places (server, other players)
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class Player extends AbstractPlayer{

    public Player(){

    }

    /**
     * Constructs a player from and AbstractPlayer
     * @param player The AbstractPlayer
     */
    public Player(player.AbstractPlayer player) {
        this.id = player.getId();
        this.listenPort = player.getListenPort();
        this.playerAddress = player.getPlayerAddress();
    }

    /**
     * Constructs a player from a GreetRequest
     * @param player the GreetRequest
     */
    public Player(P2PServiceOuterClass.Player player) {
        this.id = player.getId();
        this.listenPort = player.getListenPort();
        this.playerAddress = player.getPlayerAddress();
        this.x = player.getX();
        this.y = player.getY();
    }

    @Override
    public String toString() {
        return "Player{" +
                "id=" + id +
                ", address=" + playerAddress + ":"+ listenPort +
                ", x=" + x +
                ", y=" + y +
                '}';
    }
}

