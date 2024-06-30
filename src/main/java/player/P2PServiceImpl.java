package player;


import com.google.protobuf.Empty;
import io.grpc.Context;
import p2p.P2PServiceOuterClass;
import p2p.P2PServiceOuterClass.GreetResponse;


import io.grpc.stub.StreamObserver;
import p2p.P2PServiceGrpc;

import java.util.Queue;


public class P2PServiceImpl extends P2PServiceGrpc.P2PServiceImplBase {
    /**
     * The player receiving the messages
     */
    private final Player receivingPlayer;

    public P2PServiceImpl(Player player) {
        this.receivingPlayer = player;
    }
    /*
     * The receiving player (server) receives a presentation message with sender data.
     * the rpc saves the calling player in the peers list.
     */
    @Override
    public void presentSelf(P2PServiceOuterClass.Player request, StreamObserver<GreetResponse> responseObserver) {
        beans.Player newPlayer = new beans.Player(request);
        receivingPlayer.addPeer(newPlayer);
        P2PServiceOuterClass.Phase ph = P2PServiceOuterClass.Phase.valueOf(receivingPlayer.getPhase().name());
        responseObserver.onNext(GreetResponse.newBuilder().setPhase(ph).build());
        responseObserver.onCompleted();
    }

    /*
     * When election message is received, the receiving player responds (OK/ALIVE) and starts an election.
     */
    @Override
    public void election(Empty request, StreamObserver<P2PServiceOuterClass.OkResponse> responseObserver) {
        System.out.println("Player " + receivingPlayer.id + " received election message");
        responseObserver.onNext(P2PServiceOuterClass.OkResponse.newBuilder().build());
        receivingPlayer.startElection(false);
        responseObserver.onCompleted();
    }

    /*
     * When a coordinator (victory) message is received, notify the main thread.
     */
    @Override
    public void coordinator(P2PServiceOuterClass.CoordinatorId request, StreamObserver<Empty> responseObserver) {
        receivingPlayer.getGameSynchronizer().notifyConsensus();
        responseObserver.onNext(Empty.newBuilder().build());
        responseObserver.onCompleted();
    }

    /*
     * Received tag request, if home base acquired or this player is safe send false, else send true and set tagged state
     */
    @Override
    public void tag(Empty request, StreamObserver<P2PServiceOuterClass.TagResponse> responseObserver) {
        RAResource homeBase = receivingPlayer.getHomeBase();
        if (homeBase.isHeld() || receivingPlayer.isSafe() || receivingPlayer.getPhase() != Phase.GAME){
            responseObserver.onNext(P2PServiceOuterClass.TagResponse.newBuilder().setTagged(false).build());
        }else{
            System.out.println("Player " + receivingPlayer.id + ": got tagged");
            receivingPlayer.setTagged();
            // interrupt waiting for home base
            homeBase.renounceToAcquire();

            responseObserver.onNext(P2PServiceOuterClass.TagResponse.newBuilder().setTagged(true).build());
            responseObserver.onCompleted();
        }
    }

    /*
     * Received request to acquire home base:
     * - if this player does not need it, answer OK.
     * - if this player is using home base, it answers by not granting access (not ok) and queues the request
     * - if this player wants to use home base, but it did not yet, compare the timestamp of the request with the one
     * used in this player's own request. The earliest wins: if this player's timestamp is bigger
     * this player answers OK, else it queues the request.
     */
    @Override
    public void acquireHomeBase(P2PServiceOuterClass.HomeBaseRequest request, StreamObserver<P2PServiceOuterClass.OkResponse> responseObserver) {
        RAResource h = receivingPlayer.getHomeBase();
        P2PServiceOuterClass.OkResponse r = P2PServiceOuterClass.OkResponse.newBuilder().setOk(h.handleRequest(request)).build();
        responseObserver.onNext(r);
        responseObserver.onCompleted();

    }

    /*
     * Received ok from a peer that queued this player's request for home base, adds the response to the received
     * authorizations
     */
    @Override
    public void releaseHomeBase(P2PServiceOuterClass.OkResponse request, StreamObserver<Empty> responseObserver) {
        RAResource homeBase = receivingPlayer.getHomeBase();
        homeBase.addAuthorization(request);
        responseObserver.onNext(Empty.newBuilder().build());
        responseObserver.onCompleted();
    }

    /*
     * Received outcome, add it to the list of outcomes
     */
    @Override
    public void notifyOutcome(P2PServiceOuterClass.PlayerOutcome request, StreamObserver<Empty> responseObserver) {
        if (request.getSafe()){
            receivingPlayer.removeFromGame(new beans.Player(request.getPlayer()));
        }
        receivingPlayer.addOutcome(request);
        responseObserver.onNext(Empty.newBuilder().build());
        responseObserver.onCompleted();
    }

    /*
     * Received end game message, set phase to END.
     */
    @Override
    public void endGame(Empty request, StreamObserver<Empty> responseObserver) {
        System.out.println("Player " + receivingPlayer.id + ": received message from seeker: game has ended");
        receivingPlayer.setPhase(Phase.END);
        receivingPlayer.getGameSynchronizer().notifyEnd();
        responseObserver.onNext(Empty.newBuilder().build());
        responseObserver.onCompleted();
    }

    /*
     * Received message to go to preparation, set phase only if game has ended.
     */
    @Override
    public void goToPreparation(Empty request, StreamObserver<Empty> responseObserver) {
        synchronized (receivingPlayer.getPhaseLock()) {
            if (receivingPlayer.getPhase() == Phase.END)
                receivingPlayer.setPhase(Phase.PREPARATION);
        }

        responseObserver.onNext(Empty.newBuilder().build());
        responseObserver.onCompleted();
    }
}
