package org.skywalking.apm.agent.core.boot;

/**
 * The <code>BootService</code> is an interface to all remote, which need to boot when plugin mechanism begins to
 * work.
 * {@link #boot()} will be called when <code>BootService</code> start up.
 *
 * @author wusheng
 */
public interface BootService {
    void beforeBoot() throws Throwable;

    void boot() throws Throwable;

    void afterBoot() throws Throwable;
}
