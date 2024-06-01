package beans;

import java.util.List;
import java.util.Collections;
public class HeartRateMeasurements {
    public final int id;
    public final long timestamp;
    public final List<Double> averageHRList;

    public HeartRateMeasurements(int id, long timestamp, List<Double> averageHRList) {
        this.id = id;
        this.timestamp = timestamp;
        this.averageHRList = Collections.unmodifiableList(averageHRList);
    }


    @Override
    public String toString() {
        return "HeartRateMeasurements{" +
                "id=" + id +
                ", timestamp=" + timestamp +
                ", averageHRList=" + averageHRList +
                '}';
    }
}
