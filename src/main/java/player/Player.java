package player;

import com.google.gson.Gson;
import com.google.protobuf.Empty;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;


import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.*;

import beans.AddPlayerResponse;


import io.grpc.ServerBuilder;
import org.eclipse.paho.client.mqttv3.*;
import p2p.P2PServiceOuterClass;
import player.simulator.HRSimulator;
import player.simulator.SlidingWindowBuffer;

/**
 * A player of the WatchOut game. A player is able to register to the administration server, to participate in
 * electing the seeker and playing the game.
 */
public class Player extends AbstractPlayer{

    private final String adminAddress;
    private final List<beans.Player> peers = new ArrayList<>();;
    private final Set<beans.Player> inGamePeers = new HashSet<>();
    private volatile Phase phase = Phase.UNKNOWN;
    private final Object phaseLock = new Object();
    private final RAResource homeBase = new RAResource();
    private final GRPCClient grpcClient;
    private io.grpc.Server grpcServer;
    private final GameSynchronizer gameSynchronizer = new GameSynchronizer();
    private volatile boolean isSeeker = false;
    private volatile boolean isSafe = false;
    private boolean isTagged = false;
    private final Object taggedLock = new Object();
    private final List<P2PServiceOuterClass.PlayerOutcome> outcomes = new ArrayList<>();
    private BroadcastResponses<P2PServiceOuterClass.PlayerOutcome> syncOutcomes;

    private SlidingWindowBuffer slidingWindowBuffer;
    private SlidingWindowConsumer slidingWindowConsumer;
    private List<Double> averageHRList;
    private HRSimulator hrSimulator;
    private MeasurementSender measurementSender;

    /**
     * Constructs a Player initializing the necessary fields
     * @param id The id of this player
     * @param port The listen port that is used to receive gRPC communication from other peers
     * @param adminAddress the address of the administration server
     */
    public Player(int id, int port, String adminAddress) {
        this.id = id;
        this.listenPort = port;
        this.adminAddress = adminAddress;
        grpcClient =  new GRPCClient(this);
        startGameLoop();
    }

    private void resetGameVariables(){
        isSafe = false;
        isTagged = false;
        outcomes.clear();
        syncOutcomes = null;

    }

    /**
     * Registers this player to the administration server, presents it to the other players, and starts the WatchOut
     * game loop according to the phase of the game at the time of this player's entry. Returns printing the error, if
     * registration to the administration server failed.
     */
    private void startGameLoop(){
        startGRPC();
        int status = registerToServer();
        if (status == 409) {
            System.out.println("id " + id + " already registered, try again");
            stopGRPC();
            return;
        }else if (status != 200){
            System.out.println("Failed to reach administration server, error " + status);
            stopGRPC();
            return;
        }

        // start sensor data related threads
        startSimulator();
        // present to already registered players
        BroadcastResponses<P2PServiceOuterClass.GreetResponse> greetResponses;
        synchronized(peers){
            greetResponses = new BroadcastResponses<>(peers);
            for (beans.Player p : peers){
                grpcClient.presentToPeer(p,greetResponses);
            }
        }
        List<P2PServiceOuterClass.GreetResponse> phases = greetResponses.getAll();
        startMqtt();
        // if phase is unknown get the most advanced phase among the responses
        if (phase == Phase.UNKNOWN) {
            Phase mostAdvanced = phase;
            for (P2PServiceOuterClass.GreetResponse phaseResponse : phases){
                Phase p = Phase.valueOf(phaseResponse.getPhase().name());
                if(p.ordinal() > mostAdvanced.ordinal()) mostAdvanced = p;
            }
            phase = mostAdvanced;
        }
        System.out.println("Player " + id + ": phase = " + phase.name());
        if (phase == Phase.ELECTION) startElection(true);
        if (phase == Phase.GAME){
            play();
            gameSynchronizer.waitEnd();
            resetGameVariables();
        }
        while(true){
            gameSynchronizer.waitConsensus();
            play();
            gameSynchronizer.waitEnd();
            resetGameVariables();

        }
    }

    /**
     * Starts heart rate sensor simulator. Every 10 seconds, the collected measurements are sent to the administration
     * server.
     */
    private void startSimulator() {
        slidingWindowBuffer = new SlidingWindowBuffer();
        averageHRList = new ArrayList<>();

        hrSimulator = new HRSimulator(slidingWindowBuffer);
        slidingWindowConsumer = new SlidingWindowConsumer(slidingWindowBuffer, averageHRList);
        measurementSender = new MeasurementSender(id, adminAddress, averageHRList);

        hrSimulator.start();
        slidingWindowConsumer.start();
        measurementSender.start();
        System.out.println("Simulator started!");
    }

    /**
     * broadcasts the outcome of the game for this player.
     * @param safe the outcome, it is true if this player is safe, false if it is eliminated
     */
    public void broadCastOutcome(boolean safe){
        synchronized (peers) {
            for (beans.Player p : peers) {
                grpcClient.sendOutcome(p,safe);
            }
        }
    }

    /**
     * Sets this player status to tagged
     */
    public void setTagged(){
        synchronized (taggedLock) {
            isTagged = true;
        }
    }

    /**
     * Adds the specified player outcome to the list of received ones. It requires lock of the outcome list
     * @param outcome the outcome to add
     */
    public void addOutcome(P2PServiceOuterClass.PlayerOutcome outcome){
        synchronized(outcomes){
            outcomes.add(outcome);
            if (syncOutcomes != null) syncOutcomes.add(outcome);
        }
    }

    /**
     * Removes the specified peer from the peers still in the game.
     * @param p the peer to remove
     */
    public void removeFromGame(beans.Player p) {
        synchronized (peers){
            inGamePeers.remove(p);
        }
    }

    /**
     *
     * @return the home base RAResource object
     */
    public RAResource getHomeBase() {
        return homeBase;
    }


    /**
     *
     * @return the game synchronizer that can be used to signal the main thread about various stages of the game.
     */
    public GameSynchronizer getGameSynchronizer() {
        return gameSynchronizer;
    }


    /**
     *
     * @return true if this player is safe (reached the home base and waited 10 sec), false otherwise
     */
    public boolean isSafe() {
        return isSafe;
    }


    /**
     *
     * @return The current phase according to this player
     */
    public Phase getPhase() {
        return phase;
    }

    /**
     *
     * @return the lock on the phase object of this player
     */
    public Object getPhaseLock() {
        return phaseLock;
    }

    /**
     * Sets the phase of this player.
     * @param phase the phase
     */
    public void setPhase(Phase phase) {
        this.phase = phase;
    }

    /**
     * Executes a post request to the rest server, in order to register this player to the game. Sets
     * this player coordinates and peers.
     * @return the http status code for the request.
     */
    public int registerToServer() {
        Client client = Client.create();
        String postPath = "/players/add";
        WebResource webResource = client.resource(adminAddress + postPath);
        String input = new Gson().toJson(new beans.Player(this));
        int code = 500;
        try {
            ClientResponse clientResponse = webResource.type("application/json").post(ClientResponse.class, input);
            code = clientResponse.getStatus();
            if (clientResponse.getStatus() == 200) {
                AddPlayerResponse playerResponse = clientResponse.getEntity(AddPlayerResponse.class);
                System.out.println(playerResponse.toString());
                this.x = playerResponse.getX();
                this.y = playerResponse.getY();
                if (playerResponse.getPlayers() != null) {
                    this.phase = playerResponse.getPlayers().size() < 2 ? Phase.PREPARATION : Phase.UNKNOWN;
                    synchronized (peers) {
                        peers.addAll(playerResponse.getPlayers());
                    }
                }else{
                    this.phase = Phase.PREPARATION;
                }
            }
        } catch (ClientHandlerException e) {
            System.out.println("Server not available");
            e.printStackTrace();
            System.exit(1);
        }
        return code;
    }

    /**
     * Adds a player to the peers of this player. It requires the lock on the peers collection
     * @param p The player to add
     */
    public void addPeer(beans.Player p){
        // need to synchronize because grpc server uses multithreading, this method will be called
        // by grpc server
        synchronized (peers) {
            peers.add(p);
            inGamePeers.add(p);
        }
    }
    /**
     * Starts gRPC server for this player, to receive messages from peers.
     */
    private void startGRPC() {
        try {
            grpcServer = ServerBuilder.forPort(listenPort).addService(new P2PServiceImpl(this)).build();
            grpcServer.start();
            System.out.println("GRPC server started!");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Stops gRPC server for this player.
     */
    private void stopGRPC() {
        if(grpcServer != null) grpcServer.shutdown();
    }

    /**
     * Connects this player's mqtt client and subscribes to topic where administration client sends messages, setting
     * the appropriate callback to start the game and print the received message.
     */
    private void startMqtt() {
        String broker = "tcp://localhost:1883";
        String clientId = MqttClient.generateClientId();
        String topic = "WatchOut/broadcast";
        int qos = 2;
        MqttClient mqttClient;

        try {
            mqttClient = new MqttClient(broker, clientId);
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);

            // Connect the client
            System.out.println(clientId + " Connecting Broker " + broker);
            mqttClient.connect(connOpts);
            System.out.println(clientId + " Connected - Thread PID: " + Thread.currentThread().getId());

            // Callback
            mqttClient.setCallback(new MqttCallback() {
                // Called when a message arrives from the administration client
                public void messageArrived(String topic, MqttMessage message) {

                    String receivedMessage = new String(message.getPayload());
                    if (topic.equals("WatchOut/broadcast")) {
                        if (receivedMessage.equals("start")) {
                            // if game is in end state return, the other phases will get handled by startElection method
                            if (phase == Phase.END) return;
                            System.out.println("Game manager started the game, starting election of seeker");
                            startElection(false);
                        }else{
                            System.out.println("Message received from game manager: " + receivedMessage);
                        }
                    }

                }

                public void connectionLost(Throwable cause) {
                    System.out.println(clientId + " Connection lost! cause:" + cause.getMessage() + "-  Thread PID: " + Thread.currentThread().getId());
                    System.out.println(cause.toString());
                }

                public void deliveryComplete(IMqttDeliveryToken token) {
                    // Not used here
                }

            });

            System.out.println(clientId + " Subscribing ... - Thread PID: " + Thread.currentThread().getId());
            mqttClient.subscribe(topic, qos);
            System.out.println(clientId + " Subscribed to topic : " + topic);

        } catch (MqttException me) {
            System.out.println("reason " + me.getReasonCode());
            System.out.println("msg " + me.getMessage());
            System.out.println("loc " + me.getLocalizedMessage());
            System.out.println("cause " + me.getCause());
            System.out.println("exception " + me);
            me.printStackTrace();
        }
    }



    /**
     * Starts an election with a distributed algorithm. It is assumed that players do not crash/leave, so the timeout of
     * election request is not handled. Once a consensus is reached, it is notified through the GameSynchronizer object.
     * If an election is already ongoing, the player is playing or the phase of the game is unknown, it does nothing,
     * unless <code>forceElection</code> is set to true.
     * @param forceElection if true, it starts the election even if the phase does not foresee it
     */
    public void startElection(boolean forceElection){
        synchronized (phaseLock){
            if ((phase == Phase.ELECTION || phase == Phase.GAME || phase == Phase.UNKNOWN || isSeeker)
                && !forceElection){
                return;
            }
            phase = Phase.ELECTION;
        }
        System.out.println("Starting election...");
        // bully algorithm
        List<beans.Player> higherPriorityPeers = new ArrayList<>();
        // synchronize to prevent players entering while determining if this player has the highest priority.
        synchronized (peers) {
            BroadcastResponses<Empty> victoryAcks = new BroadcastResponses<>(peers);

            for (beans.Player p : peers) {
                // higher priority is given to players closer to the home base in the center.
                // in case of equal distance, higher id has priority.
                // double == comparison shouldn't be an issue for the use case.
                if (
                        p.distanceFromCenter() < this.distanceFromCenter() ||
                        (p.distanceFromCenter() == this.distanceFromCenter() && this.id < p.id)
                ) {
                    higherPriorityPeers.add(p);
                }
            }

            if (higherPriorityPeers.isEmpty()) {
                System.out.println("Player " + this.id + " won the election");
                // this player has higher priority, so send victory.
                for (beans.Player p : peers) {
                    grpcClient.sendVictory(p,victoryAcks);
                }
                // wait for every response
                victoryAcks.getAll();
                // once all peers have responded, notify consensus -> phase = game
                isSeeker = true;
                this.phase = Phase.GAME;
                gameSynchronizer.notifyConsensus();
                return;
            }
            
        }
        System.out.println("Player " + this.id + ": sending election messages to higher priority peers");
        for (beans.Player p : higherPriorityPeers) {
            System.out.println(p);
            grpcClient.sendElection(p);
        }

    }

    /**
     * Once called, this player starts to play, according to its role.
     */
    private void play(){
        synchronized (phaseLock) {
            phase = Phase.GAME;
        }
        System.out.println("Player " + this.id + ": playing game");
        if (isSeeker) playSeeker();
        else playHider();
    }


    /**
     * Once called, this player plays as seeker. It tries to tag the closest player until they are all safe or tagged,
     * moving at a speed of 2 m/s. Then, it handles the end of the game, synchronizing the other players on the end
     * and preparation phases.
     */
    private void playSeeker(){

        int currentX = x, currentY = y;
        synchronized (peers) {
            inGamePeers.addAll(peers);
        }
        while (true) {
            beans.Player closest = new beans.Player();
            double closestDistance = Double.MAX_VALUE;
            synchronized (peers) {
                // if no player remain in the game, the game has ended.
                if (inGamePeers.isEmpty()) {
                    synchronized (outcomes) {
                        syncOutcomes = new BroadcastResponses<>(peers);
                        for (P2PServiceOuterClass.PlayerOutcome o : outcomes) {
                            syncOutcomes.add(o);
                        }
                    }
                    phase = Phase.END;
                    break;
                }
                // find the closest player, need to remain synchronized to avoid adding while iterating
                for (beans.Player p : inGamePeers) {
                    double d = Point2D.distance(currentX,currentY,p.getX(),p.getY());
                    if (d < closestDistance) {
                        closest = p;
                        closestDistance = d;
                    }
                }

            }
            // release lock and start moving towards closest player
            // speed: 2 m/s, coordinates are tens of meters, 10*sqrt( (x1-x2)^2+(y1-y2)^2 ) =
            // = sqrt( 10^2 * (x1-x2)^2+ 10^2 * (y1-y2)^2 )
            closestDistance = closestDistance * 10;

            long timeMillis = (long)Math.ceil(closestDistance/0.002);
            System.out.println("Player " + id + ": moving towards player: " + closest.id + ", distance: "
                    + closestDistance + " time: " + timeMillis/1000 + " seconds");
            try {
                Thread.sleep(timeMillis);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            // try to tag the player
            grpcClient.tryTag(closest);
            //either the player is tagged and eliminated, or it moved to home base, so it is out of the game
            synchronized (peers) {
                inGamePeers.remove(closest);
            }

            currentX = closest.getX();
            currentY = closest.getY();
        }

        // end game, wait for all players outcome, then send end message and wait for responses.
        System.out.println("Player "+ id +":Game over, waiting for all players outcome");
        List<P2PServiceOuterClass.PlayerOutcome> results = syncOutcomes.getAll();
        for (P2PServiceOuterClass.PlayerOutcome o : results){
            System.out.println("Player " + o.getPlayer().getId() + ": " + (o.getSafe() ? "safe" : "tagged" ));
        }
        BroadcastResponses<Empty> endAcks;
        endAcks = new BroadcastResponses<>(peers);
        for (beans.Player p : peers) {
            grpcClient.sendEndGame(p,endAcks);
        }
        endAcks.getAll();
        System.out.println("Player "+ id +": all players are synchronized on game end, returning to preparation");
        // once all peers are in end state, message to return to preparation can be sent and seeker role must be dropped.
        isSeeker = false;
        phase = Phase.PREPARATION;
        synchronized (peers) {
            for (beans.Player p : peers) {
                grpcClient.sendGoToPreparation(p);
            }
        }
        gameSynchronizer.notifyEnd();
    }


    /**
     * Starts this player to play as hider. A hider tries to access the home base with a distributed mutual exclusion
     * algorithm, once it gets authorization, it starts moving towards it with a speed of 2 m/s. Once reached, it waits
     * 10 seconds, after which it is considered safe and broadcasts it to the other players.
     */
    private void playHider(){
        // need to atomically check for tagged and request access, so that the thread receiving the tag message can
        // either set the isTagged variable before this block, or interrupt the waiting of the authorizations after it.
        synchronized (taggedLock){
            if (isTagged) {
                broadCastOutcome(false);
                return;
            }
            synchronized (peers) {
                homeBase.requestAccess(grpcClient,peers);
            }
        }

        System.out.println("Player " + id + ": waiting for home base, timestamp:" + homeBase.getAcquireTimestamp());
        // wait for all authorization to be received, then move to home base and wait 10 seconds
        homeBase.acquire();
        // if got tagged while waiting release resource

        if(homeBase.didRenounceToAcquire()){
            homeBase.release(grpcClient);
            broadCastOutcome(false);
            return;
        }
        double distanceFromCenter = distanceFromCenter() * 10;
        long timeToCenter = (long)Math.ceil(distanceFromCenter/0.002);
        System.out.println("Player " + this.id + ": acquired home base," + "time to safety: "
                + timeToCenter/1000 + " + 10 seconds");
        try {
            Thread.sleep(timeToCenter + (10 * 1000));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        // broadcast safety
        System.out.println("Player " + this.id + ": safe");
        isSafe = true;
        homeBase.release(grpcClient);
        broadCastOutcome(true);

    }

}

