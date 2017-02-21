package com.a.eye.skywalking.collector.cluster.message;

import com.a.eye.skywalking.trace.TraceSegment;

import java.io.Serializable;

//#messages
public interface TraceMessages {

  public static class TransformationJob implements Serializable {
    private final String text;
    private final TraceSegment traceSegment;

    public TransformationJob(String text, TraceSegment traceSegment) {
      this.text = text;
      this.traceSegment = traceSegment;
    }

    public String getText() {
      return text;
    }

    public TraceSegment getTraceSegment() {
      return traceSegment;
    }
  }

  public static class TransformationResult implements Serializable {
    private final String text;

    public TransformationResult(String text) {
      this.text = text;
    }

    public String getText() {
      return text;
    }

    @Override
    public String toString() {
      return "TransformationResult(" + text + ")";
    }
  }

  public static class JobFailed implements Serializable {
    private final String reason;
    private final TransformationJob job;

    public JobFailed(String reason, TransformationJob job) {
      this.reason = reason;
      this.job = job;
    }

    public String getReason() {
      return reason;
    }

    public TransformationJob getJob() {
      return job;
    }

    @Override
    public String toString() {
      return "JobFailed(" + reason + ")";
    }
  }

  public static final String BACKEND_REGISTRATION = "BackendRegistration";

}
//#messages