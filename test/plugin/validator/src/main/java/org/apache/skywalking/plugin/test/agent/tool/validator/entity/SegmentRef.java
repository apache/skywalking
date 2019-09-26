package org.apache.skywalking.plugin.test.agent.tool.validator.entity;

/**
 * Created by xin on 2017/7/15.
 */
public interface SegmentRef {

    String parentServiceId();

    String parentServiceName();

    String networkAddressId();

    String entryServiceId();

    String refType();

    String parentSpanId();

    String parentTraceSegmentId();

    String parentApplicationInstanceId();

    String networkAddress();

    String entryServiceName();

    void parentTraceSegmentId(String parentTraceSegmentId);

    String entryApplicationInstanceId();
}
