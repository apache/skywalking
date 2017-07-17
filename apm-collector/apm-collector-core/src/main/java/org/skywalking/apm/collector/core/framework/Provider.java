package org.skywalking.apm.collector.core.framework;

/**
 * @author pengys5
 */
public interface Provider<D> {
    D create();
}
