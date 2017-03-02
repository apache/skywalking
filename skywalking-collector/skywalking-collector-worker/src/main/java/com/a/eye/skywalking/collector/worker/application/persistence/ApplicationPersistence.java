package com.a.eye.skywalking.collector.worker.application.persistence;

import com.a.eye.skywalking.collector.worker.persistence.PersistenceMessage;
import com.a.eye.skywalking.collector.worker.persistence.PersistenceWorker;

import java.util.HashMap;
import java.util.Map;

/**
 * @author pengys5
 */
public class ApplicationPersistence extends PersistenceWorker<Object> {

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
