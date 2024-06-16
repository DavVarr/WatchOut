package administrator.server;

import beans.AddPlayerResponse;
import beans.HeartRateMeasurements;
import beans.Player;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
/**
 * ServerService class provides RESTful endpoints for managing players and heart rate measurements.
 */
@Path("players")
public class ServerService {

    /**
     * Adds a new player to the server.
     *
     * @param player the player to add
     * @return A Response indicating the outcome of the add operation. The HTTP status code is 200 if the id
     * of the player is unique, 409 conflict otherwise.
     */
    @Path("add")
    @POST
    @Consumes({"application/json", "application/xml"})
    @Produces({"application/json", "application/xml"})
    public Response addPlayer(Player player) {
        AddPlayerResponse addPlayerResponse = Server.getInstance().addPlayer(player);
        if (addPlayerResponse != null) {
            return Response.ok(addPlayerResponse).build();
        } else {
            return Response.status(Response.Status.CONFLICT).entity("Id is already used by another player").build();
        }
    }

    /**
     * Retrieves all players from the server.
     *
     * @return A Response containing a list of all players
     */
    @Path("get-all")
    @GET
    @Produces({"application/json", "application/xml"})
    public Response getPlayers() {
        return Response.ok(Server.getInstance().getPlayers()).build();
    }

    /**
     * Adds heart rate measurements to the server.
     *
     * @param hr the heart rate measurements to add
     * @return A Response indicating the outcome of the add operation
     */
    @Path("heart-rate")
    @POST
    @Consumes({"application/json", "application/xml"})
    public Response addHRMeasurements(HeartRateMeasurements hr){
        Server.getInstance().addHRMeasurements(hr);
        return Response.ok().build();
    }

    /**
     * Retrieves from server the average of the last N heart rate measurements for a specified player.
     * @param n the number of measurements to consider
     * @param playerId the ID of the player
     * @return A Response containing the average of the last N heart rate measurements. The HTTP status code is
     * 200 if the request is successful, 400 BAD REQUEST if n <= 0 or n is bigger than the number of measurements
     * stored at the server, and 404 NOT FOUND if there are no heart rate measurements for the specified player.
     */
    @Path("heart-rate/average/last-n")
    @GET
    @Produces({"application/json", "application/xml"})
    public Response getAverageLastNHR(@QueryParam("n") int n, @QueryParam("player") int playerId) {
        Server s = Server.getInstance();
        if (n<= 0) return Response.status(Response.Status.BAD_REQUEST).entity("'n' param must be > 0, instead got:"+ n).build();
        if (! s.existsPlayerHRMeasurements(playerId)) 
            return Response.status(Response.Status.NOT_FOUND).entity("there are no measurements for player "+ playerId).build();
        int measurementsCount = s.measurementsCount(playerId);
        if (n > measurementsCount) 
            return Response.status(Response.Status.BAD_REQUEST).entity("'n' too big, got:"+ n + " but there are "+ measurementsCount +" HR values").build();
        return Response.ok(s.getAverageLastNHR(n, playerId)).build();
    }

    /**
     * Retrieves the average heart rate measurements between two specified times.
     *
     * @param t1 the start time in milliseconds
     * @param t2 the end time in milliseconds
     * @return A Response containing the average heart rate measurements between the specified times. The HTTP status
     * code is 200 if the request is successful, 400 BAD REQUEST if there are no heart rate measurements, and 404
     * NOT FOUND if there are no measurements between the specified time stamps
     */
    @Path("heart-rate/average/between-time")
    @GET
    @Produces({"application/json", "application/xml"})
    public Response getAverageHRBetweenTime(@QueryParam("t1") long t1, @QueryParam("t2") long t2) {
        if (t1 > t2){
            long temp = t1;
            t1 = t2;
            t2 = temp;
        }
        Server s = Server.getInstance();
        if (! s.anyMeasurement()) return Response.status(Response.Status.BAD_REQUEST).entity("There are no HR measurements yet").build();
        Double average = Server.getInstance().getAverageRangeHR(t1, t2);
        if (average != null){
            return Response.ok(average).build();
        }else{
            return Response.status(Status.NOT_FOUND).entity("No heart rate measurements found between " + t1 + " and " + t2).build();
        }
    }
}
