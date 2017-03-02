package com.a.eye.skywalking.collector.worker.persistence;

import com.a.eye.skywalking.collector.actor.AbstractWorker;
import com.a.eye.skywalking.collector.worker.MetricCollection;

/**
 * @author pengys5
 */
public class AppResponseCost extends AbstractWorker {

    private MetricCollection oneSecondsLessMetric = new MetricCollection();

    private MetricCollection threeSecondsLessMetric = new MetricCollection();

    private MetricCollection fiveSecondsLessMetric = new MetricCollection();

    private MetricCollection slowSecondsLessMetric = new MetricCollection();

    private MetricCollection errorSecondsLessMetric = new MetricCollection();

    @Override
    public void receive(Object message) throws Throwable {
        if (message instanceof AppResponseSummaryMessage) {
            AppResponseCostMessage costMessage = (AppResponseCostMessage) message;
            long cost = costMessage.getEndTime() - costMessage.getStartTime();
            if (cost <= 1000 && !costMessage.getError()) {
                oneSecondsLessMetric.put(costMessage.getTimeSlice(), costMessage.getCode(), cost);
            } else if (cost > 1000 && cost <= 3000 && !costMessage.getError()) {
                threeSecondsLessMetric.put(costMessage.getTimeSlice(), costMessage.getCode(), cost);
            } else if (cost > 3000 && cost <= 5000 && !costMessage.getError()) {
                fiveSecondsLessMetric.put(costMessage.getTimeSlice(), costMessage.getCode(), cost);
            } else if (cost > 5000 && cost <= 5000 && !costMessage.getError()) {
                slowSecondsLessMetric.put(costMessage.getTimeSlice(), costMessage.getCode(), cost);
            } else {
                errorSecondsLessMetric.put(costMessage.getTimeSlice(), costMessage.getCode(), cost);
            }
        }
    }
}
