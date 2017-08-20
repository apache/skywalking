package org.skywalking.apm.collector.core.stream;

/**
 * @author pengys5
 */
public interface Transform<T> {
    Data toData();

    T toSelf(Data data);
}
