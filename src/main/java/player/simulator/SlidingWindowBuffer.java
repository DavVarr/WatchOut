package player.simulator;

import java.util.ArrayList;
import java.util.List;

public class SlidingWindowBuffer implements Buffer {

    private final List<Measurement> buffer;

    public SlidingWindowBuffer() {
        this.buffer = new ArrayList<>();
    }

    @Override
    public synchronized void addMeasurement(Measurement m) {
        buffer.add(m);
        notify();
    }

    @Override
    public synchronized List<Measurement> readAllAndClean() {

        while (buffer.size() < 8) {
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        List<Measurement> window = new ArrayList<>(buffer.subList(0, 8));
        buffer.subList(0, 4).clear();

        return window;
    }
}
