package org.skywalking.apm.agent.core.dictionary;

import io.netty.util.internal.ConcurrentSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Map of application id to application code, which is from the collector side.
 *
 * @author wusheng
 */
public enum ApplicationDictionary {
    INSTANCE;
    private Map<String, Integer> applicationDictionary = new ConcurrentHashMap<String, Integer>();
    private Set<String> unRegisterApplication = new ConcurrentSet<String>();

    public PossibleFound find(String applicationCode) {
        Integer applicationId = applicationDictionary.get(applicationCode);
        if (applicationId != null) {
            return new Found(applicationId);
        } else {
            unRegisterApplication.add(applicationCode);
            return new NotFound();
        }
    }
}
