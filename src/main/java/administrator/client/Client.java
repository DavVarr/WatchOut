package administrator.client;


import beans.Player;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.core.util.MultivaluedMapImpl;

import javax.ws.rs.core.MultivaluedMap;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.List;

/**
 * A class that collects the administration client functionalities
 */
class Client {
    private final String serverAddress = "http://localhost:1337";
    private final com.sun.jersey.api.client.Client client;
    private MqttClient mqttClient;
    private final String mqttClientId;

    /**
     * Constructs a client, initializing jersey and mqtt clients.
     */
    public Client() {
        client = com.sun.jersey.api.client.Client.create();
        String broker = "tcp://localhost:1883";
        mqttClientId = MqttClient.generateClientId();
        
        try {
            mqttClient = new MqttClient(broker, mqttClientId);
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);

            // Connect the client
            System.out.println(mqttClientId + " Connecting to Broker " + broker);
            mqttClient.connect(connOpts);
            System.out.println(mqttClientId + " Connected - Thread PID: " + Thread.currentThread().getId());
        }catch (MqttException me){
            handleMqttException(me);
        }
    }

    private void handleMqttException(MqttException me){
        System.out.println("reason " + me.getReasonCode());
        System.out.println("msg " + me.getMessage());
        System.out.println("loc " + me.getLocalizedMessage());
        System.out.println("cause " + me.getCause());
        System.out.println("excep " + me);
        me.printStackTrace();
    }

    /**
     * Performs a GET request to the specified url
     * @param url The url
     * @return The response
     */
    private ClientResponse getRequest(String url) {
        return getRequest(url, new MultivaluedMapImpl());
    }

    /**
     * Performs a GET request to the specified url, with the specified parameters
     * @param url The url
     * @param queryParam The request parameters
     * @return The response
     */
    private ClientResponse getRequest(String url, MultivaluedMap<String, String> queryParam) {
        WebResource webResource = client.resource(url).queryParams(queryParam);
        try {
            return webResource.type("application/json").get(ClientResponse.class);
        } catch (ClientHandlerException e) {
            System.out.println("Server not available");
            return null;
        }
    }
    /**
     * @return the list of players currently registered to the administration server.
     * if server is not available, returns null
     */
    public List<Player> getPlayers() {
        String getPath = "/players/get-all";
        ClientResponse clientResponse = getRequest(serverAddress + getPath);
        if (clientResponse == null) return null;
        String body = clientResponse.getEntity(String.class).toString();
        List<Player> players = new Gson().fromJson(body, new TypeToken<List<Player>>() {
        }.getType());
        return players;
    }

    /** Prints the average of the last n heart rate measurements sent to the server by a given player, if server is available.
     * @param n the number of last heart rate measurements
     * @param player the id of the player
     */
    public void printAverageLastNHR(int n, int player) {
        MultivaluedMapImpl queryParam = new MultivaluedMapImpl();
        queryParam.add("n", n);
        queryParam.add("player", player);
        String getPath = "/players/heart-rate/average/last-n";
        ClientResponse clientResponse = getRequest(serverAddress + getPath, queryParam);
        if (clientResponse == null) return;
        String body = clientResponse.getEntity(String.class).toString();
        if (clientResponse.getStatus() == 200) {
            System.out.println("The average of the last " + n + " heart rate measurements sent to the server by the player " + player + " is " + body);
        } else {
            System.out.println("Error " + clientResponse.getStatus() + ":" + body);
        }
    }

    /** Prints average of the heart rate measurements sent by all the players to the server and occurred between
     * timestamps t1 and t2, if server is available.
     * @param t1 first timestamp
     * @param t2 second timestamp
     */
    public void printAverageRangeHR(long t1, long t2) {
        MultivaluedMapImpl queryParam = new MultivaluedMapImpl();
        queryParam.add("t1", String.valueOf(t1));
        queryParam.add("t2", String.valueOf(t2));
        String getPath = "/players/heart-rate/average/between-time";
        ClientResponse clientResponse = getRequest(serverAddress + getPath, queryParam);
        if (clientResponse == null) return;
        String body = clientResponse.getEntity(String.class).toString();
        if (clientResponse.getStatus() == 200) {
            System.out.println("The average of the heart rate measurements sent by all the players to the server and occurred between timestamps " + t1 + " and " + t2 + " is " + body);
        } else {
            System.out.println("Error " + clientResponse.getStatus() + ":" + body);
        }
    }

    /** Sends a message through mqtt to the players subscribed to the specified topic
     * @param payload the message to be sent
     * @param topic the topic in which to publish the message
     */
    private void publishMessage(String payload, String topic) {
        MqttMessage message = new MqttMessage(payload.getBytes());
        message.setQos(2);
        System.out.println(mqttClientId + " Publishing message: " + payload + " ...");
        
        try {
            mqttClient.publish(topic, message);
            System.out.println(mqttClientId + " Message published");
        } catch (MqttException me) {
            handleMqttException(me);
        }
    }

    /**
     * Sends a message through mqtt to the players
     * @param message the message to be sent
     */
    public void broadcastMessage(String message){
        publishMessage(message, "WatchOut/broadcast");
    }
    

}
