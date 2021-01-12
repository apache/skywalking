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

package org.apache.skywalking.apm.testcase.activemq.controller;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/case")
public class CaseController {

    private static final Logger LOGGER = LogManager.getLogger(CaseController.class);

    @Value("${activemq.server}")
    private String brokenUrl;

    private static final String USER_NAME = ActiveMQConnection.DEFAULT_USER;
    private static final String PASSWORD = ActiveMQConnection.DEFAULT_PASSWORD;

    private static final String SUCCESS = "Success";
    private static final String FAIL = "Fail";

    @RequestMapping("/activemq")
    @ResponseBody
    public String testcase() {
        Session session = null;
        Connection connection = null;
        try {
            ConnectionFactory factory = new ActiveMQConnectionFactory(USER_NAME, PASSWORD, brokenUrl);
            connection = factory.createConnection();
            connection.start();
            session = connection.createSession(Boolean.TRUE, Session.AUTO_ACKNOWLEDGE);
            Destination destination = session.createQueue("test");
            MessageProducer messageProducer = session.createProducer(destination);
            TextMessage message = session.createTextMessage("test");
            messageProducer.send(message);
            session.commit();
            session.close();
            connection.close();
        } catch (Exception ex) {
            LOGGER.error(ex);
            try {
                session.close();
                connection.close();
            } catch (JMSException e) {
                LOGGER.error(e);
            }
        }
        new ConsumerThread().start();
        return SUCCESS;
    }

    @RequestMapping("/healthCheck")
    @ResponseBody
    public String healthCheck() {
        Session session = null;
        Connection connection = null;
        try {
            ConnectionFactory factory = new ActiveMQConnectionFactory(USER_NAME, PASSWORD, brokenUrl);
            connection = factory.createConnection();
            connection.start();
            connection.getMetaData().getJMSVersion();
            connection.close();
        } catch (Exception ex) {
            LOGGER.error(ex);
            try {
                session.close();
                connection.close();
            } catch (JMSException e) {
                LOGGER.error(e);
            }
            return FAIL;
        }
        return SUCCESS;
    }

    public class ConsumerThread extends Thread {
        @Override
        public void run() {
            Session session = null;
            Connection connection = null;
            try {
                ConnectionFactory factory = new ActiveMQConnectionFactory(USER_NAME, PASSWORD, brokenUrl);
                connection = factory.createConnection();
                connection.start();
                session = connection.createSession(Boolean.TRUE, Session.AUTO_ACKNOWLEDGE);
                Destination destination = session.createQueue("test");
                MessageConsumer messageConsumer = session.createConsumer(destination);
                messageConsumer.receive();
                session.close();
                connection.close();
            } catch (Exception ex) {
                LOGGER.error(ex);
                try {
                    session.close();
                    connection.close();
                } catch (JMSException e) {
                    LOGGER.error(e);
                }
            }
        }
    }
}
