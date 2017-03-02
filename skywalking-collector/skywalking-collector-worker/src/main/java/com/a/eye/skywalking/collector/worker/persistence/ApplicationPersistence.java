package com.a.eye.skywalking.collector.worker.persistence;

import com.a.eye.skywalking.collector.actor.AbstractWorker;

import java.util.HashMap;
import java.util.Map;

/**
 * @author pengys5
 */
public class ApplicationPersistence extends AbstractWorker<Object> {

    private Map<String, ApplicationMessage> appData = new HashMap();

    @Override
    public void receive(Object message) throws Throwable {
        if (message instanceof ApplicationMessage) {
            ApplicationMessage applicationMessage = (ApplicationMessage) message;
            appData.put(applicationMessage.getCode(), applicationMessage);
        } else if (message instanceof PersistenceMessage) {
            
        }
    }
}
