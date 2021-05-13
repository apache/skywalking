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

package org.apache.skywalking.apm.testcase.canal.controller;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.client.CanalConnectors;
import com.alibaba.otter.canal.protocol.Message;
import java.net.InetSocketAddress;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/case")
@PropertySource("classpath:application.properties")
public class CaseController {

    private static final String SUCCESS = "Success";

    private static final Logger LOGGER = LoggerFactory.getLogger(CaseController.class);

    @Value(value = "${canal.host}")
    private String address;

    @Value(value = "${canal.port}")
    private int port;

    @RequestMapping("/canal-case")
    @ResponseBody
    public String canalCase() {
        wrapCreateConnector("example", connector -> {
            try {
                int batchSize = 1000;
                connector.subscribe(".*\\..*");
                connector.rollback();
                Message message = connector.getWithoutAck(batchSize);
                long batchId = message.getId();
                LOGGER.info(message.getEntries().toString());
                connector.ack(batchId);
            } catch (Exception ex) {
                LOGGER.error(ex.toString());
            }
        });
        return SUCCESS;
    }

    @RequestMapping("/healthCheck")
    @ResponseBody
    public String healthCheck() {
        wrapCreateConnector("healthCheck", connect -> {
        });
        return SUCCESS;
    }

    private void wrapCreateConnector(String destination, Consumer<CanalConnector> consumer) {
        CanalConnector connector = CanalConnectors.newSingleConnector(new InetSocketAddress(address, port), destination, "", "");
        connector.connect();
        try {
            consumer.accept(connector);
        } finally {
            connector.disconnect();
        }
    }
}
