package beans;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;
import java.util.Collections;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)

public class HeartRateMeasurements {
    private int id;
    private long timestamp;
    private List<Double> averageHRList;

    public HeartRateMeasurements(){

    }

    public HeartRateMeasurements(int id, long timestamp, List<Double> averageHRList) {
        this.id = id;
        this.timestamp = timestamp;
        this.averageHRList = Collections.unmodifiableList(averageHRList);
    }


    public int getId() {
        return id;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public List<Double> getAverageHRList() {
        return averageHRList;
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
