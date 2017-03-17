package com.a.eye.skywalking.trace.TraceId;

/**
 * The <code>DistributedTraceId</code> presents a distributed call chain.
 *
 * This call chain has an unique (service) entrance,
 *
 * such as: Service : http://www.skywalking.com/cust/query, all the services, called behind this service, rest services,
 * db executions, are using the same <code>DistributedTraceId</code> even in different JVM.
 *
 * The <code>DistributedTraceId</code> contains only one string, and can NOT be reset, creating a new instance is the
 * only option.
 *
 * @author wusheng
 */
public abstract class DistributedTraceId {
    private String id;

    public DistributedTraceId(String id) {
        this.id = id;
    }

    public String get() {
        return id;
    }
}
