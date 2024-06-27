package player;

import java.util.HashSet;
import java.util.Set;

/**
 * A class that enables a thread to wait until a particular stage of the WatchOutGame is reached.
 */
public class GameSynchronizer {
    private final Set<String> messages;

    /**
     * Construct a new GameSynchronizer.
     */
    public GameSynchronizer(){
        messages = new HashSet<>();
    }

    /**
     * Calling thread waits until the specified message is added to this instance's state, then consumes it by removing
     * it.
     * @param m the message
     */
    private synchronized void waitMessage(String m){
        while(!messages.contains(m)){
            try{
                wait();
            }catch(InterruptedException e){
                throw new RuntimeException(e);
            }
        }
        // message received, remove it from set
        messages.remove(m);
    }

    /**
     * Adds the specified message to this instance's state.
     * @param m the message
     */
    private synchronized void addMessage(String m){
        messages.add(m);
        notifyAll();
    }


    /**
     * Notifies every thread waiting for consensus.
     */
    public void notifyConsensus(){
        addMessage("consensus");
    }

    /**
     * Waits until some other thread calls notifyConsensus().
     */
    public void waitConsensus(){
        waitMessage("consensus");
    }

    /**
     * Notifies every thread waiting for a victory message.
     */
    public void notifyVictory(){
        addMessage("victory");
    }

    /**
     * Waits until some other thread calls notifyVictory().
     */
    public void waitVictory(){
        waitMessage("victory");
    }

    /**
     * Notifies every thread waiting for the end of the game.
     */
    public void notifyEnd(){
        addMessage("end");
    }
    /**
     * Waits until some other thread calls notifyEnd().
     */
    public void waitEnd(){
        waitMessage("end");
    }

}
