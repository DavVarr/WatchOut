package player;

import java.util.List;

import player.simulator.Measurement;
import player.simulator.SlidingWindowBuffer;

class SlidingWindowConsumer extends Thread {
    private final SlidingWindowBuffer slidingWindowBuffer;
    private final List<Double> averageHRList;

    public SlidingWindowConsumer(SlidingWindowBuffer slidingWindowBuffer, List<Double> averageHRList) {
        this.slidingWindowBuffer = slidingWindowBuffer;
        this.averageHRList = averageHRList;
    }


    @Override
    public void run() {
        while (true) {
            double average = slidingWindowBuffer.readAllAndClean()
                    .stream().mapToDouble(Measurement::getValue)
                    .average().orElse(0);

            synchronized (averageHRList) {
                averageHRList.add(average);
            }

        }
    }
}
