package org.skywalking.apm.collector.core.remote;

/**
 * @author pengys5
 */
public interface Remote {
    void call(Object message);
}
