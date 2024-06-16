package player;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.awt.geom.Point2D;

/**
 * Abstract class that collects all fields and methods common to both the player class that is used to store
 * data and the actual player class that implements game logic.
 */
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

    /**
     * Computes distance of this from home base (that is points (4,4),(4,5),(5,4),(5,5)).
     * @return the distance
     */
    public double distanceFromCenter(){
        if (x <= 4 && y <=4 ) {
            return Point2D.distance(x,y,4,4);
        }
        else if (x <= 4 && y >=5 ) {
            return Point2D.distance(x,y,4,5);
        } else if (x >= 5 && y <=4 ) {
            return Point2D.distance(x,y,5,4);
        } else{
            return Point2D.distance(x,y,5,5);
        }
    }

}
