package administrator.server;

import beans.AddPlayerResponse;
import beans.HeartRateMeasurements;
import beans.Player;

import com.sun.jersey.api.container.httpserver.HttpServerFactory;
import com.sun.net.httpserver.HttpServer;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.OptionalDouble;
import java.util.Random;
import java.util.stream.Collectors;



class Server {
    private final String HOST = "localhost";
    private final int PORT = 1337;
    private final Map<Integer,Player> players;
    private final Map<Integer, List<HeartRateMeasurements>> measurementsMap;
    private HttpServer httpServer;
    private static Server instance;

    private Server() {
        players = new HashMap<>();
        measurementsMap = new HashMap<>();
        startRest();
    }

    //singleton
    public synchronized static Server getInstance() {
        if (instance == null) {
            instance = new Server();
        }
        return instance;
    }

    public void shutdown() {
        System.out.println("Shutdown server");
        stopRest();
    }
    /*
     * Adds a player to the players data structure, if it has a unique id.
     * Returns the generated coordinates for the player and the list of the other already connected players,
     * or null if the id is not unique.
     */
    public AddPlayerResponse addPlayer(Player player) {
        synchronized (players) {
            if (players.containsKey(player.getId())) {
                return null;
            }

            Random random = new Random();
            int x = random.nextInt(10);
            int y = random.nextInt(10);
            if (x != 0 && x != 9 && y != 0 && y != 9){ // if not on the perimeter of the grid
                int[] vals = {0,9};
                if (random.nextBoolean()){ // with probability 1/2 set randomly x or y to 0 or 9
                    x = vals[random.nextInt(2)];
                }else{
                    y = vals[random.nextInt(2)];
                }
            }

            AddPlayerResponse response = new AddPlayerResponse(x, y, new ArrayList<>(players.values()));
            player.setX(x); player.setY(y);
            players.put(player.getId(),player);
            return response;
            }
        }


    public List<Player> getPlayers() {
        synchronized (players) {
            return new ArrayList<>(players.values());
        }
    }

    public void addHRMeasurements(HeartRateMeasurements measure){
        synchronized (measurementsMap){
            List<HeartRateMeasurements> measurementsList = measurementsMap.get(measure.id);
            if (measurementsList == null) {
                measurementsMap.put(measure.id, new ArrayList<>(Collections.singletonList(measure)));
            } else {
                measurementsList.add(measure);
            }
        }
    }

    private void startRest() {
        try {
            httpServer = HttpServerFactory.create("http://" + HOST + ":" + PORT + "/");
            httpServer.start();
            System.out.println("Rest server started on: http://" + HOST + ":" + PORT);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void stopRest() {
        System.out.println("Stopping Rest server");
        httpServer.stop(0);
        System.out.println("Rest server stopped");
    }

    public boolean existsPlayerHRMeasurements(int id){
        synchronized (measurementsMap) {
            return measurementsMap.containsKey(id) && !measurementsMap.get(id).isEmpty();
        }
    }

    public int measurementsCount(int playerId) {
        synchronized (measurementsMap) {
        return measurementsMap.get(playerId).stream().map(a -> a.averageHRList.size()).reduce((a, b) -> a+b).get();
        }
    }

    public boolean anyMeasurement(){
        synchronized (measurementsMap) {
            if (measurementsMap.values() == null || measurementsMap.values().isEmpty()) {
                return false;
            }else return true;
        }
    }

    /*
     * Computes the average of the last n heart rate measurements.
     * if n > # measurements || n < 0  throws IllegalArgumentException
     * if the player does not have measurements throws NoSuchElementException
     */
    public Double getAverageLastNHR(int n, int playerId) {
        synchronized (measurementsMap) {
            List<HeartRateMeasurements> playerMeasurementsList = measurementsMap.get(playerId);
            if ( playerMeasurementsList == null || playerMeasurementsList.isEmpty()) throw new NoSuchElementException("Player "+playerId+" does not have measurements");
            List<Double> allValues = playerMeasurementsList.stream()
                .flatMap(m -> m.averageHRList.stream())
                .collect(Collectors.toList());

            if (n > allValues.size() || n < 0) {
                throw new IllegalArgumentException("Invalid last n parameter, negative or too big, n:" + n +" player measurements:"+ allValues.size());
            }

            return allValues.stream()
                .skip(allValues.size() - n)
                .mapToDouble(Double::doubleValue)
                .average()
                .getAsDouble();
        }

         
    }

    /*
     * Computes the average of all heart rate measurements between timestamp t1 and t2.
     * throws IllegalArgumentException if t1 > t2.
     * throws IllegalStateException if there are no measurements.
     * returns the average or null if there are no measurements between timestamp t1 and t2.
     */
    public Double getAverageRangeHR(long t1, long t2) {
        if (t1 > t2) throw new IllegalArgumentException("Invalid timestamps. The start timestamp must be less than or equal to the end timestamp.");
        synchronized (measurementsMap) {
            if (!anyMeasurement()) {
                throw new IllegalStateException("The list of heart rate measurements is empty.");
            }

            OptionalDouble optionalAverage = measurementsMap.values().stream()
                .flatMap(List::stream) // Flatten the collection of lists into a stream of HeartRateMeasurements
                .filter(m -> m.timestamp >= t1 && m.timestamp <= t2) // Filter by timestamp
                .flatMap(m -> m.averageHRList.stream()) // Flatten the averageHRList into a stream of Double
                .mapToDouble(Double::doubleValue)
                .average();
            if (optionalAverage.isPresent()) {
                return optionalAverage.getAsDouble();
            }else return null;
        }
    }
}
