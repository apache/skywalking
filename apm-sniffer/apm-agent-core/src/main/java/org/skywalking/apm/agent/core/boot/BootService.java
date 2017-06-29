package org.skywalking.apm.agent.core.boot;

/**
 * The <code>BootService</code> is an interface to all remote, which need to boot when plugin mechanism begins to
 * work.
 * {@link #bootUp()} will be called when <code>BootService</code> start up.
 *
 * @author wusheng
 */
public interface BootService {
    void bootUp() throws Throwable;
}
