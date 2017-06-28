package org.skywalking.apm.agent.core.dictionary;

/**
 * @author wusheng
 */
public class DictionaryManager {
    /**
     * @return {@link ApplicationDictionary} to find application id for application code and network address.
     */
    public static ApplicationDictionary findApplicationCodeSection() {
        return ApplicationDictionary.INSTANCE;
    }

    /**
     * @return {@link OperationNameDictionary} to find service id.
     */
    public static OperationNameDictionary findOperationNameCodeSection() {
        return OperationNameDictionary.INSTANCE;
    }
}
