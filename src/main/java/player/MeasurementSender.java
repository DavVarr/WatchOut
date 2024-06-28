package player;

import java.util.ArrayList;
import java.util.List;

import beans.HeartRateMeasurements;
import com.google.gson.Gson;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import org.eclipse.paho.client.mqttv3.MqttMessage;

class MeasurementSender extends Thread {
    private final int id;
    private final String adminAddress;
    private final List<Double> averageHRList;
    Client client = Client.create();

    public MeasurementSender(int id, String adminAddress, List<Double> averageHRList) {
        this.id = id;
        this.adminAddress = adminAddress;
        this.averageHRList = averageHRList;

    }


    public ClientResponse postRequest(String url, HeartRateMeasurements heartRateMeasurements){
        WebResource webResource = client.resource(url);
        String input = new Gson().toJson(heartRateMeasurements);
        try {
            return webResource.type("application/json").post(ClientResponse.class, input);
        } catch (ClientHandlerException e) {
            System.out.println("[HRMeasurementsSender] Server not available");
            return null;
        }
    }


    @Override
    public void run() {

            while (true) {
                try {
                    Thread.sleep(1000 * 10);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }

                HeartRateMeasurements hr;
                synchronized (averageHRList) {
                    ArrayList<Double> measurements = new ArrayList<>(averageHRList);
                    hr = new HeartRateMeasurements(id, System.currentTimeMillis(),measurements);
                    averageHRList.clear();
                }
                postRequest(adminAddress,hr);
            }



    }
}
