package player;

import com.google.protobuf.Empty;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import p2p.P2PServiceGrpc;
import p2p.P2PServiceOuterClass;

/**
 * A class that collects methods to allow players to perform gRPC client calls
 */
public class GRPCClient {
    Player client;

    /**
     * Constructs a GRPCClient with the specified player as the client who will make the requests.
     * @param client the player that will make the requests.
     */
    GRPCClient(Player client){
        this.client = client;
    }

    /**
     * Sends this player information to peer p through gRPC (async). Adds the response to the BroadcastResponses object
     * @param p The player to send the message to
     * @param r The BroadcastResponses object
     */
    public void presentToPeer(beans.Player p, BroadcastResponses<P2PServiceOuterClass.GreetResponse> r){
        //opening a connection with server
        ManagedChannel channel = ManagedChannelBuilder.forTarget(p.playerAddress + ":" + p.listenPort).usePlaintext().build();

        //creating the asynchronous stub
        P2PServiceGrpc.P2PServiceStub stub = P2PServiceGrpc.newStub(channel);

        P2PServiceOuterClass.Player greetRequest = P2PServiceOuterClass.Player.newBuilder().setId(client.getId())
                .setListenPort(client.getListenPort())
                .setX(client.getX())
                .setY(client.getY())
                .build();
        System.out.println("Sending GRPC [presentSelf] to player " + p.getId());
        stub.presentSelf(greetRequest, new StreamObserver<P2PServiceOuterClass.GreetResponse>() {
            // all the methods here are CALLBACKS which are handled in an asynchronous manner.
            @Override
            public void onNext(P2PServiceOuterClass.GreetResponse value) {
                //add the response to the list of broadcast
                r.add(value);
            }
            @Override
            public void onError(Throwable throwable) {
                System.out.println(throwable.getMessage());
            }
            @Override
            public void onCompleted() {
                System.out.println("Closed connection GRPC [presentSelf] to player " + p.getId());
                channel.shutdown();
            }
        });


    }

    /**
     * Sends an election message to peer p through gRPC (async).
     * @param p The player to send the message to
     */
    public void sendElection(beans.Player p){
        ManagedChannel channel = ManagedChannelBuilder.forTarget(p.playerAddress + ":" + p.listenPort).usePlaintext().build();
        P2PServiceGrpc.P2PServiceStub stub = P2PServiceGrpc.newStub(channel);
        Empty e = Empty.newBuilder().build();
        stub.election(e, new StreamObserver<P2PServiceOuterClass.OkResponse>() {
            @Override
            public void onNext(P2PServiceOuterClass.OkResponse value) {
                // response received -> alive.
            }

            @Override
            public void onError(Throwable t) {
                System.out.println(t.getMessage());
            }

            @Override
            public void onCompleted() {

            }
        });

    }

    /**
     * Sends a victory message to peer p through gRPC (async), announcing this as seeker. Adds the response to the
     * BroadcastResponses object
     * @param p The player to send the message to
     * @param r The BroadcastResponses object
     */
    public void sendVictory(beans.Player p, BroadcastResponses<Empty> r){
        ManagedChannel channel = ManagedChannelBuilder.forTarget(p.playerAddress + ":" + p.listenPort).usePlaintext().build();
        P2PServiceGrpc.P2PServiceStub stub = P2PServiceGrpc.newStub(channel);
        P2PServiceOuterClass.CoordinatorId coorId = P2PServiceOuterClass.CoordinatorId.newBuilder().setId(client.getId()).build();
        stub.coordinator(coorId, new StreamObserver<Empty>() {
            @Override
            public void onNext(Empty value) {
                // response received -> p knows this is the coordinator
                r.add(value);
            }

            @Override
            public void onError(Throwable t) {
                System.out.println(t.getMessage());
            }

            @Override
            public void onCompleted() {

            }
        });

    }

    /**
     * Sends a tag message to peer p through gRPC (async).
     * @param p the player to send the message to
     */
    public void tryTag(beans.Player p){
        ManagedChannel channel = ManagedChannelBuilder.forTarget(p.playerAddress + ":" + p.listenPort).usePlaintext().build();
        P2PServiceGrpc.P2PServiceStub stub = P2PServiceGrpc.newStub(channel);
        stub.tag(Empty.newBuilder().build(), new StreamObserver<P2PServiceOuterClass.TagResponse>() {
            @Override
            public void onNext(P2PServiceOuterClass.TagResponse value) {

            }

            @Override
            public void onError(Throwable t) {
                System.out.println(t.getMessage());
            }

            @Override
            public void onCompleted() {

            }
        });

    }

    /**
     * Sends a message to peer p through gRPC (async), containing the outcome of the game for this player (if he's
     * eliminated or safe).
     * @param p the player to send the message to
     * @param o the outcome. true if safe, false if eliminated
     */
    public void sendOutcome(beans.Player p, boolean o) {
        ManagedChannel channel = ManagedChannelBuilder.forTarget(p.playerAddress + ":" + p.listenPort).usePlaintext().build();
        P2PServiceGrpc.P2PServiceStub stub = P2PServiceGrpc.newStub(channel);
        stub.notifyOutcome(P2PServiceOuterClass.PlayerOutcome.newBuilder().setSafe(o).build(), new StreamObserver<Empty>() {
            @Override
            public void onNext(Empty value) {

            }

            @Override
            public void onError(Throwable t) {
                System.out.println(t.getMessage());
            }

            @Override
            public void onCompleted() {

            }
        });
    }

    /**
     * Sends a message to peer p through gRPC (async), asking permission to acquire the specified resource.
     * Adds the response to the received authorizations, if it is != null.
     * @param p the player to send the message to
     * @param r the resource to acquire
     */
    public void askResourceAccess(beans.Player p,RAResource r){
        ManagedChannel channel = ManagedChannelBuilder.forTarget(p.playerAddress + ":" + p.listenPort).usePlaintext().build();
        P2PServiceGrpc.P2PServiceStub stub = P2PServiceGrpc.newStub(channel);
        long t = r.getAcquireTimestamp();
        P2PServiceOuterClass.HomeBaseRequest request  = P2PServiceOuterClass.HomeBaseRequest.newBuilder().setTimestamp(t).build();
        stub.acquireHomeBase(request,new StreamObserver<P2PServiceOuterClass.OkResponse>() {

            @Override
            public void onNext(P2PServiceOuterClass.OkResponse value) {
                r.addAuthorization(value);
            }

            @Override
            public void onError(Throwable t) {
                System.out.println(t.getMessage());
            }

            @Override
            public void onCompleted() {

            }
        });
    }

    /**
     * Sends a message to peer p through gRPC (async), signaling that this player released the specified resource
     * @param p the player to send the message to
     * @param r the resource to release
     */
    // in this case the home base is the only resource, so r isn't used.
    public void notifyResourceRelease(P2PServiceOuterClass.HomeBaseRequest.PlayerConnection p,RAResource r){
        ManagedChannel channel = ManagedChannelBuilder.forTarget(p.getPlayerAddress() + ":" + p.getListenPort()).usePlaintext().build();
        P2PServiceGrpc.P2PServiceStub stub = P2PServiceGrpc.newStub(channel);
        P2PServiceOuterClass.OkResponse request = P2PServiceOuterClass.OkResponse.newBuilder().setOk(true).build();
        stub.releaseHomeBase(request, new StreamObserver<Empty>() {

            @Override
            public void onNext(Empty value) {
                // do nothing
            }

            @Override
            public void onError(Throwable t) {
                System.out.println(t.getMessage());
            }

            @Override
            public void onCompleted() {

            }
        });
    }

    /**
     * Sends a message to peer p through gRPC (async), announcing the end of the game. Adds the response to the
     * BroadcastResponses object
     * @param p The player to send the message to
     * @param r The BroadcastResponses object
     */
    public void sendEndGame(beans.Player p, BroadcastResponses<Empty> r){
        ManagedChannel channel = ManagedChannelBuilder.forTarget(p.getPlayerAddress() + ":" + p.getListenPort()).usePlaintext().build();
        P2PServiceGrpc.P2PServiceStub stub = P2PServiceGrpc.newStub(channel);
        stub.endGame(Empty.newBuilder().build(), new StreamObserver<Empty>() {
            @Override
            public void onNext(Empty value) {
                r.add(value);
            }

            @Override
            public void onError(Throwable t) {

            }

            @Override
            public void onCompleted() {

            }

            });
    }

    /**
     * Sends a message to peer p through gRPC (async), signaling to move to preparation phase.
     * @param p The player to send the message to
     */
    public void sendGoToPreparation(beans.Player p){
        ManagedChannel channel = ManagedChannelBuilder.forTarget(p.getPlayerAddress() + ":" + p.getListenPort()).usePlaintext().build();
        P2PServiceGrpc.P2PServiceStub stub = P2PServiceGrpc.newStub(channel);
        stub.endGame(Empty.newBuilder().build(), new StreamObserver<Empty>() {
            @Override
            public void onNext(Empty value) {
            }

            @Override
            public void onError(Throwable t) {

            }

            @Override
            public void onCompleted() {

            }

        });
    }

}
