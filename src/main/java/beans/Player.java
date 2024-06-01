package beans;



import player.AbstractPlayer;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class Player extends AbstractPlayer{

    public Player(){

    }
    public Player(player.AbstractPlayer player) {
        this.id = player.getId();
        this.listenPort = player.getListenPort();
        this.playerAddress = player.getPlayerAddress();
    }



    @Override
    public String toString() {
        return "Player{" +
                "id=" + id +
                ", address=" + playerAddress + ":"+ listenPort +
                '}';
    }
}

