package com.a.eye.skywalking.collector.worker.persistence;

import com.a.eye.skywalking.collector.actor.AbstractWorker;
import com.a.eye.skywalking.collector.worker.MetricCollection;

/**
 * @author pengys5
 */
public class AppResponseSummary extends AbstractWorker {

    private MetricCollection summaryMetric = new MetricCollection();

    private MetricCollection errorSummaryMetric = new MetricCollection();

    private MetricCollection successSummaryMetric = new MetricCollection();

    @Override
    public void receive(Object message) throws Throwable {
        if (message instanceof AppResponseSummaryMessage) {
            AppResponseSummaryMessage summaryMessage = (AppResponseSummaryMessage) message;
            summaryMetric.put(summaryMessage.getTimeSlice(), summaryMessage.getCode(), 1l);
            if (summaryMessage.getError()) {
                errorSummaryMetric.put(summaryMessage.getTimeSlice(), summaryMessage.getCode(), 1l);
            } else {
                successSummaryMetric.put(summaryMessage.getTimeSlice(), summaryMessage.getCode(), 1l);
            }
        }
    }
}
