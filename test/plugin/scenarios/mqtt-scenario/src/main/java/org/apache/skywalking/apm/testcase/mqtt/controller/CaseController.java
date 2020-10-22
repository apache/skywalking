/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.apm.testcase.mqtt.controller;

import javax.annotation.PostConstruct;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttTopic;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/case")
@PropertySource("classpath:application.properties")
public class CaseController {

    @Value("${mqtt.server:tcp://127.0.0.1:1883}")
    private String brokerUrl;

    public static final String CLIENTID = "mqtt_server_plugin_test";

    private static final String USERNAME = "admin";

    private static final String PASSWORD = "public";

    private static final String TOPIC = "skywalking-agent";

    private MqttClient mqttClient;

    @PostConstruct
    public void init() throws MqttException {
        mqttClient = new MqttClient(brokerUrl, CLIENTID, new MemoryPersistence());
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setUserName(USERNAME);
        options.setPassword(PASSWORD.toCharArray());
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(20);
        mqttClient.setCallback(new MqttCallback() {

            @Override
            public void connectionLost(final Throwable cause) {
                System.out.println(CLIENTID + " connectionLost " + cause);
            }

            @Override
            public void messageArrived(final String topic, final MqttMessage message) throws Exception {
                System.out.println(topic + " messageArrived: " + message);
            }

            @Override
            public void deliveryComplete(final IMqttDeliveryToken token) {
                System.out.println(CLIENTID + " deliveryComplete " + token);
            }
        });
        mqttClient.connect(options);
        mqttClient.subscribe(TOPIC);
    }

    @RequestMapping("/mqtt")
    @ResponseBody
    public String mqttCase() throws MqttException {
        MqttMessage message = new MqttMessage();
        message.setQos(0);
        message.setRetained(true);
        message.setPayload("{\"info\":\"skywalking\"}".getBytes());
        MqttTopic mqttTopic = mqttClient.getTopic(TOPIC);
        return mqttTopic.publish(message).getMessage().toString();
    }

    @RequestMapping("/healthcheck")
    public String healthCheck() {
        if (!mqttClient.isConnected()) {
            throw new RuntimeException("Mqtt not ready");
        }
        return "Success";
    }
}
