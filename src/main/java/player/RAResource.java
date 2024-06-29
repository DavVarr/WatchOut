package player;

import beans.Player;
import p2p.P2PServiceOuterClass;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Queue;

/**
 * A class that represents a resource accessed by a player (calling thread) in mutual exclusion within a distributed environment,
 * managed through Ricart and Agrawala algorithm. A RAResource is for this reason associated with a player (calling thread),
 * and holds information about its intention on the resource. So, an instance of this class is intended to be used only
 * by threads that represent the same player (it is the only possibility, since players are nodes of a distributed system).
 * The methods are properly synchronized to ensure the algorithm correctness. Furthermore, the methods to
 * acquire and release this instance, are intended to be called by the same thread (not thread safe).
 */
public class RAResource {
    // this class is only needed to represent the home base, but can be extended by fully implementing ricart and
    // agrawala, adding resource id as a field and modifying the methods and communication according to the use case.
    private volatile ResourceStatus status;
    /**
     * The queue of the pending authorizations for the home base. It contains the players
     * that asked for the home base, but did not receive an ok because this player needed to access
     * before them.
     */
    private final Queue<P2PServiceOuterClass.HomeBaseRequest.PlayerConnection> pendingAuthorizations;
    /**
     * The received authorizations to access this RAResource. If no request is ongoing (that is if status == not needed)
     * this field is null.
     */
    private volatile BroadcastResponses<P2PServiceOuterClass.OkResponse> authorizations;
    private volatile long acquireTimestamp;

    /**
     * initializes a RAResource, setting the status as not needed, the received authorizations to acquire the resource
     * to null, the queue of pending authorizations to an empty queue, and the timestamp to acquire access to Long.MAX_VALUE.
     */
    RAResource(){
        status = ResourceStatus.NOT_NEEDED;
        pendingAuthorizations = new LinkedList<>();
        acquireTimestamp = Long.MAX_VALUE;
    }

    /**
     * @return the timestamp of the last request to acquire the home base. If no request is ongoing
     * returns Long.MAX_VALUE
     */
    public synchronized long getAcquireTimestamp() {
        return acquireTimestamp;
    }

    /**
     *
     * @return true if this RAResource is held, false otherwise
     */
    public synchronized boolean isHeld(){
        return status == ResourceStatus.HELD;
    }

    /**
     *
     * @return true if acquire was called, but while waiting renounceToAcquire() was called, false otherwise
     */
    public synchronized boolean didRenounceToAcquire() {
        if (authorizations == null) return false;
        return authorizations.didStopEarly();
    }

    /**
     * Renounce to acquire this RAResource by stopping to wait for authorizations. It is still needed to call release
     * after this method. if status != needed it does nothing.
     */
    public synchronized void renounceToAcquire(){
        if (status != ResourceStatus.NEEDED) return;
        authorizations.stopEarly();
    }

    /**
     * Adds the response to the received authorizations, only if it contains true (OK).
     * If this RAResource is not needed by the player, it does nothing.
     * @param response the OkResponse to add
     */
    public synchronized void addAuthorization(P2PServiceOuterClass.OkResponse response){
        if(status == ResourceStatus.NOT_NEEDED) return;
        if (response.getOk()) authorizations.add(response);
    }

    /**
     * Sets this resource status to needed, setting the timestamp and initializing the authorizations object
     * with the input collection atomically. Then sends requests to peers in order to acquire the resource.
     * Requires external synchronization on the peers' collection if multiple thread access it.
     * If called when already trying to acquire or holding the resource, it does nothing.
     * @param peers the peers who need to approve the resource access
     * @param client the GRPCClient used to perform requests
     */
    public void requestAccess(GRPCClient client,Collection<Player> peers){

        // need to atomically set these variables to ensure consistent state
        synchronized (this){
            if (status != ResourceStatus.NOT_NEEDED) return;
            status = ResourceStatus.NEEDED;
            // assume clocks are synchronized between nodes of the distributed system (like lamport algorithm can ensure)
            acquireTimestamp = System.currentTimeMillis();
            authorizations = new BroadcastResponses<>(peers);
        }
        for (beans.Player p : peers) {
            client.askResourceAccess(p,this);
        }


    }

    /**
     * Waits until all authorizations are received, then acquires the resource. It must be called after requestAccess,
     * otherwise it does nothing and returns.
     */
    public void acquire(){
        synchronized (this) {
            if (status != ResourceStatus.NEEDED) return;
        }
        authorizations.getAll();
        status = ResourceStatus.HELD;
    }

    /**
     * Releases this resource, atomically updating its status to not needed, emptying the queue of pending authorizations
     * by sending each of the queued players the OK message, setting the authorizations to null and the acquireTimestamp
     * to Long.MAX_VALUE. If called without holding or waiting the resource, it does nothing.
     * @param client the GRPCClient used to send the OK messages
     */
    public synchronized void release(GRPCClient client){
        status = ResourceStatus.NOT_NEEDED;
        while (!pendingAuthorizations.isEmpty()){
            client.notifyResourceRelease(pendingAuthorizations.remove(),this);
        }
        authorizations = null;
        acquireTimestamp = Long.MAX_VALUE;
    }

    /**
     * Handles a request to acquire this resource, according to Ricart and Agrawala algorithm.
     * - if the player does not need this resource, answer OK (return true).
     * - if the player is using the resource, it queues the request and answers by not granting access (return false)
     * - if the player wants to use the resource, but it did not yet, compare the timestamp of the request with the one
     * used in this instance. The earliest wins: if this timestamp is bigger
     * it answers OK (return true), else it queues the request and returns false.
     * @param request the request to be handled
     */
    public synchronized boolean handleRequest(P2PServiceOuterClass.HomeBaseRequest request){
        if (status == ResourceStatus.NOT_NEEDED) {
            return true;
        }
        else if (status == ResourceStatus.HELD) {
            pendingAuthorizations.add(request.getPlayer());
            return false;
        }
        else {
            if (acquireTimestamp > request.getTimestamp())
                return true;
            else {
                pendingAuthorizations.add(request.getPlayer());
                return false;
            }
        }

    }

}
