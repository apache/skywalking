package org.skywalking.apm.plugin.spring.concurrent.match;

import org.skywalking.apm.agent.core.plugin.match.ClassMatch;

/**
 * {@link SuccessCallbackMatch} match the class that inherited <code>org.springframework.util.concurrent.SuccessCallback</code>
 * and not inherited <code>org.springframework.util.concurrent.FailureCallback</code>
 *
 * @author zhangxin
 */
public class SuccessCallbackMatch extends EitherInterfaceMatch {

    private static final String MATCH_INTERFACE = "org.springframework.util.concurrent.SuccessCallback";
    private static final String MUTEX_INTERFACE = "org.springframework.util.concurrent.FailureCallback";

    private SuccessCallbackMatch() {
    }

    @Override
    public String getMatchInterface() {
        return MATCH_INTERFACE;
    }

    @Override
    public String getMutexInterface() {
        return MUTEX_INTERFACE;
    }

    public static ClassMatch successCallbackMatch() {
        return new SuccessCallbackMatch();
    }
}
