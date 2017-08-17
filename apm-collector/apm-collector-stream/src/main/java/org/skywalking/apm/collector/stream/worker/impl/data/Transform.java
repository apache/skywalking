package org.skywalking.apm.collector.stream.worker.impl.data;

/**
 * @author pengys5
 */
public interface Transform<T> {
    Data toData();

    T toSelf(Data data);
}
