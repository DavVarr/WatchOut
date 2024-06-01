package beans;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class AddPlayerResponse {
    private int x;
    private int y;
    private List<Player> players;

    public AddPlayerResponse(){

    }
    public AddPlayerResponse(int x, int y, List<Player> players) {
        this.x = x;
        this.y = y;
        this.players = players;
    }

    public int getX() {
        return x;
    }
    public int getY() {
        return y;
    }
    public List<Player> getPlayers() {
        return players;
    }

    @Override
    public String toString() {
        return "AddPlayerResponse{" +
                "x=" + x +
                ", y=" + y +
                ", players=" + players +
                '}';
    }
}
