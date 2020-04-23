package org.apache.skywalking.apm.testcase.vertxeventbus.controller;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import org.apache.skywalking.apm.testcase.vertxeventbus.util.CustomMessage;
import org.apache.skywalking.apm.testcase.vertxeventbus.util.CustomMessageCodec;

public class ClusterReceiver extends AbstractVerticle {

    @Override
    public void start() {
        EventBus eventBus = getVertx().eventBus();
        eventBus.registerDefaultCodec(CustomMessage.class, new CustomMessageCodec());

        eventBus.consumer("cluster-message-receiver", message -> {
            CustomMessage replyMessage = new CustomMessage(200,
                    "a00000002", "Message sent from cluster receiver!");
            message.reply(replyMessage);
        });
    }
}
