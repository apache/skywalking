package org.skywalking.apm.plugin.spring.concurrent.match;

import org.skywalking.apm.agent.core.plugin.match.ClassMatch;

/**
 * {@link FailedCallbackMatch} match the class that inherited <code>org.springframework.util.concurrent.FailureCallback</code>
 * and not inherited <code>org.springframework.util.concurrent.SuccessCallback</code>
 *
 * @author zhangxin
 */
public class FailedCallbackMatch extends EitherInterfaceMatch {

    private static final String MATCH_INTERFACE = "org.springframework.util.concurrent.FailureCallback";
    private static final String MUTEX_INTERFACE = "org.springframework.util.concurrent.SuccessCallback";

    private FailedCallbackMatch() {

    }

    @Override public String getMatchInterface() {
        return MATCH_INTERFACE;
    }

    @Override public String getMutexInterface() {
        return MUTEX_INTERFACE;
    }

    public static ClassMatch failedCallbackMatch() {
        return new FailedCallbackMatch();
    }
}
