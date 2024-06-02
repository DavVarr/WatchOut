package player;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public abstract class AbstractPlayer {

    protected int id;
    protected int listenPort;
    protected String playerAddress = "localhost";
    protected int x;
    protected int y;

    public int getId() {
        return id;
    }

    public int getListenPort() {
        return listenPort;
    }

    public String getPlayerAddress() {
        return playerAddress;
    }


    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

}
