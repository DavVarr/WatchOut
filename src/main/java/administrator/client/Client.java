package administrator.client;

import beans.Player;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.core.util.MultivaluedMapImpl;

import javax.ws.rs.core.MultivaluedMap;
import java.util.List;

class Client {
    private final String serverAddress = "http://localhost:1337";
    private final com.sun.jersey.api.client.Client client;

    public Client() {
        client = com.sun.jersey.api.client.Client.create();
    }

    private ClientResponse getRequest(String url) {
        return getRequest(url, new MultivaluedMapImpl());
    }

    private ClientResponse getRequest(String url, MultivaluedMap<String, String> queryParam) {
        WebResource webResource = client.resource(url).queryParams(queryParam);
        try {
            return webResource.type("application/json").get(ClientResponse.class);
        } catch (ClientHandlerException e) {
            System.out.println("Server not available");
            return null;
        }
    }

    // Prints the list of players currently registered
    public void printPlayers() {
        String getPath = "/players/get-all";
        ClientResponse clientResponse = getRequest(serverAddress + getPath);
        String body = clientResponse.getEntity(String.class).toString();
        List<Player> players = new Gson().fromJson(body, new TypeToken<List<Player>>() {
        }.getType());
        System.out.println(players);
    }

    // Prints the average of the last n heart rate measurements sent to the server by a given player
    public void printAverageLastNHR(int n, int player) {
        MultivaluedMapImpl queryParam = new MultivaluedMapImpl();
        queryParam.add("n", n);
        queryParam.add("player", player);
        String getPath = "/players/heart-rate/average/last-n";
        ClientResponse clientResponse = getRequest(serverAddress + getPath, queryParam);
        String body = clientResponse.getEntity(String.class).toString();
        if (clientResponse.getStatus() == 200) {
            System.out.println("The average of the last " + n + " heart rate measurements sent to the server by the player " + player + " is " + body);
        } else {
            System.out.println("Error " + clientResponse.getStatus() + ":" + body);
        }
    }

    // Prints average of the heart rate measurements sent by all the players to the server and occurred between timestamps t1 and t2
    public void printAverageRangeHR(long t1, long t2) {
        MultivaluedMapImpl queryParam = new MultivaluedMapImpl();
        queryParam.add("t1", String.valueOf(t1));
        queryParam.add("t2", String.valueOf(t2));
        String getPath = "/players/heart-rate/average/between-time";
        ClientResponse clientResponse = getRequest(serverAddress + getPath, queryParam);
        String body = clientResponse.getEntity(String.class).toString();
        if (clientResponse.getStatus() == 200) {
            System.out.println("The average of the heart rate measurements sent by all the players to the server and occurred between timestamps " + t1 + " and " + t2 + " is " + body);
        } else {
            System.out.println("Error " + clientResponse.getStatus() + ":" + body);
        }
    }

    

}
