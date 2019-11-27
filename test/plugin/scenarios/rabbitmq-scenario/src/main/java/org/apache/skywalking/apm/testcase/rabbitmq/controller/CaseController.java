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

package org.apache.skywalking.apm.testcase.rabbitmq.controller;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.rabbitmq.client.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;


import java.io.IOException;

@RestController
@RequestMapping("/case")
@PropertySource("classpath:application.properties")
public class CaseController {

    private Logger logger = LogManager.getLogger(CaseController.class);


    private static final String USERNAME = "admin";

    private static final String PASSWORD = "admin";

    @Value(value = "${rabbitmq.host:127.0.0.1}")
    private String brokerUrl;

    private static final int PORT = 5672;

    private static final  String QUEUE_NAME = "test";

    private static final  String MESSAGE = "rabbitmq-testcase";

    @RequestMapping("/rabbitmq")
    @ResponseBody
    public String rabbitmqCase() throws Exception {
        Channel channel = null;
        Connection connection = null;

        try{
            ConnectionFactory factory = new ConnectionFactory();
            logger.info("Using brokerUrl = " + brokerUrl);
            factory.setHost(brokerUrl);
            factory.setPort(PORT);
            factory.setUsername(USERNAME);
            factory.setPassword(PASSWORD);

            connection = factory.newConnection();

            channel = connection.createChannel();
            channel.queueDeclare(QUEUE_NAME, false, false, false, null);
            AMQP.BasicProperties.Builder propsBuilder = new AMQP.BasicProperties.Builder();
            logger.info("Message being published -------------->"+MESSAGE);
            channel.basicPublish("", QUEUE_NAME, propsBuilder.build(), MESSAGE.getBytes("UTF-8"));
            logger.info("Message has been published-------------->"+MESSAGE);

            final CountDownLatch waitForConsume = new CountDownLatch(1);
            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                String message = new String(delivery.getBody(), "UTF-8");
                logger.info("Message received-------------->"+message);
                waitForConsume.countDown();
            };
            channel.basicConsume(QUEUE_NAME, true, deliverCallback, consumerTag -> { });
            waitForConsume.await(5000L, TimeUnit.MILLISECONDS);
            logger.info("Message Consumed-------------->");

        }catch (Exception ex){
            logger.error(ex.toString());
        }
        finally {
            if (channel != null) {
                try {
                    channel.close();
                }catch (Exception e){
                    // ignore
                }
            }
            if (connection != null) {
                try {
                    connection.close();
                }catch (Exception e){
                    // ignore
                }
            }
        }
        return "Success";
    }

    @RequestMapping("/healthcheck")
    public String healthCheck() throws Exception {
        Channel channel = null;
        Connection connection = null;

        try{
            ConnectionFactory factory = new ConnectionFactory();
            logger.info("Using brokerUrl = " + brokerUrl);
            factory.setHost(brokerUrl);
            factory.setPort(PORT);
            factory.setUsername(USERNAME);
            factory.setPassword(PASSWORD);

            connection = factory.newConnection();

            channel = connection.createChannel();
            channel.queueDeclare(QUEUE_NAME, false, false, false, null);
            logger.info("Completed Health Check. Able to connect to RabbitMQ and create queue-------------->");
        }catch (Exception ex){
            logger.error(ex.toString());
            throw ex;
        }
        finally {
            if (channel != null) {
                try {
                    channel.close();
                }catch (Exception e){
                    // ignore
                }
            }
            if (connection != null) {
                try {
                    connection.close();
                }catch (Exception e){
                    // ignore
                }
            }
        }
        return "Success";
    }
}
