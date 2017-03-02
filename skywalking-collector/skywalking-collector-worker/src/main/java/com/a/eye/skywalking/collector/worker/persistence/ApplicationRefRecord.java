package com.a.eye.skywalking.collector.worker.persistence;

import com.a.eye.skywalking.collector.actor.AbstractWorker;
import com.a.eye.skywalking.collector.worker.RecordCollection;
import com.a.eye.skywalking.collector.worker.application.persistence.ApplicationMessage;
import com.google.gson.JsonObject;

/**
 * @author pengys5
 */
public class ApplicationRefRecord extends AbstractWorker {

    private RecordCollection refRecord = new RecordCollection();

    @Override
    public void receive(Object message) throws Throwable {
        if (message instanceof ApplicationMessage) {
            ApplicationRefRecordMessage applicationMessage = (ApplicationRefRecordMessage) message;
            refRecord.put("", applicationMessage.getCode() + "-" + applicationMessage.getRefCode(), new JsonObject());
        } else if (message instanceof PersistenceMessage) {

        }
    }
}
