package com.a.eye.skywalking.collector.worker;

/**
 * @author pengys5
 */
public class Metric {
    private String timeSlice;
    private String metricName;
    private Long metricValue;

    public Metric(String timeSlice, String metricName, Long metricValue) {
        this.timeSlice = timeSlice;
        this.metricName = metricName;
        this.metricValue = metricValue;
    }

    public String getTimeSlice() {
        return timeSlice;
    }

    public void setTimeSlice(String timeSlice) {
        this.timeSlice = timeSlice;
    }

    public String getMetricName() {
        return metricName;
    }

    public void setMetricName(String metricName) {
        this.metricName = metricName;
    }

    public Long getMetricValue() {
        return metricValue;
    }

    public void setMetricValue(Long metricValue) {
        this.metricValue = metricValue;
    }
}
