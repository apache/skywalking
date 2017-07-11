package org.skywalking.apm.collector.core.module;

/**
 * @author pengys5
 */
public abstract class ModuleRegistration {

    protected static final String SEPARATOR = "|";

    protected abstract String buildValue();
}
