package org.skywalking.apm.collector.core.framework;

/**
 * @author pengys5
 */
public interface Loader<T> {
    T load() throws DefineException;
}
