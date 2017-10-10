package org.skywalking.apm.agent.core.plugin;

/**
 * The <code>EnhanceContext</code> represents the context or status for processing a class.
 *
 * Based on this context, the plugin core {@link org.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassEnhancePluginDefine}
 * knows how to process the specific steps for every particular plugin.
 *
 * @author wusheng
 */
public class EnhanceContext {
    private boolean isEnhanced = false;
    /**
     * The object has already been enhanced or extended.
     * e.g. added the new field, or implemented the new interface
     */
    private boolean objectExtended = false;

    public boolean isEnhanced() {
        return isEnhanced;
    }

    public void initializationStageCompleted() {
        isEnhanced = true;
    }

    public boolean isObjectExtended() {
        return objectExtended;
    }

    public void extendObjectCompleted() {
        objectExtended = true;
    }
}
