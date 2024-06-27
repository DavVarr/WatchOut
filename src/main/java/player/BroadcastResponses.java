package player;

import beans.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * A class that is used to understand when all async responses (from all peers) are received
 * in order to synchronize on all the broadcast responses.
 */
public class BroadcastResponses<T> {
    private final int requests;
    private final List<T> responses;
    private boolean earlyStop = false;

    /**
     * Constructs a new BroadcastResponses with empty responses.
     * @param peers The list of peers that will send the responses.
     */
    public BroadcastResponses(Collection<Player> peers) {
        this.requests = peers.size();
        this.responses = new ArrayList<T>();
    }

    /**
     * Blocks the calling thread until all responses are received, then returns them.
     * @return The responses.
     */
    public synchronized List<T> getAll(){
        // In the project it is assumed nodes of the distributed system do not leave/crash.
        // In that case the thread should be awakened and return after a timeout.
        while (responses.size() < requests) {
            if (earlyStop) break;
            try{
                wait();
            }catch(InterruptedException e){
                throw new RuntimeException(e);
            }

        }
        return Collections.unmodifiableList(responses);
    }

    /**
     * Saves a response in this instance, then notifies all threads that might be waiting to get the responses.
     * if the number of responses received is greater or equal than the actual requests or stopped waiting, it does nothing.
     * @param response the response to save.
     */
    public synchronized void add(T response){
        if(responses.size() >= requests || didStopEarly()) return;
        responses.add(response);
        notifyAll();
    }

    /**
     * Notifies waiting threads, signaling to stop waiting even if not all responses are received.
     */
    public synchronized void stopEarly(){
        earlyStop = true;
        notifyAll();
    }

    /**
     *
     * @return true if it was signaled to stop waiting before all response were received, false otherwise
     */
    public synchronized boolean didStopEarly(){
        return earlyStop;
    }

}
