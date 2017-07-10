package org.skywalking.apm.collector.core.framework;

import java.util.Map;

/**
 * @author pengys5
 */
public interface Define {

    void initialize(Map config) throws DefineException;

    String getName();

    void setName(String name);
}
