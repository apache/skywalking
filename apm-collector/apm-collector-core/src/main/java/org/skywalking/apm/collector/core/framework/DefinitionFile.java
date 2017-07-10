package org.skywalking.apm.collector.core.framework;

/**
 * @author pengys5
 */
public abstract class DefinitionFile {

    private final String CATALOG = "META-INF/defines/";

    protected abstract String fileName();

    public final String get() {
        return CATALOG + fileName();
    }
}
