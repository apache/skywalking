package org.skywalking.apm.agent.core.dictionary;

/**
 * @author wusheng
 */
public class DictionaryManager {
    /**
     * @return {@link ApplicationDictionary} to find applicationId
     */
    public static ApplicationDictionary findApplicationCodeSection(){
        return ApplicationDictionary.INSTANCE;
    }
}
