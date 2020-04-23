package org.apache.skywalking.apm.testcase.vertxeventbus.controller;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import org.apache.skywalking.apm.testcase.vertxeventbus.util.CustomMessage;

public class LocalReceiver extends AbstractVerticle {

    @Override
    public void start() {
        EventBus eventBus = getVertx().eventBus();
        eventBus.consumer("local-message-receiver", message -> {
            CustomMessage replyMessage = new CustomMessage(200,
                    "a00000002", "Message sent from local receiver!");
            message.reply(replyMessage);
        });
    }
}
