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

package org.apache.skywalking.oap.server.analyzer.agent.pulsar.provider.handler;

import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.Message;
import org.apache.skywalking.apm.network.language.agent.v3.MeterDataCollection;
import org.apache.skywalking.oap.server.analyzer.agent.pulsar.module.PulsarFetcherConfig;
import org.apache.skywalking.oap.server.analyzer.module.AnalyzerModule;
import org.apache.skywalking.oap.server.analyzer.provider.meter.process.IMeterProcessService;
import org.apache.skywalking.oap.server.analyzer.provider.meter.process.MeterProcessor;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * A handler deserializes the message of meter system data and pushes it to downstream.
 */
@Slf4j
public class MeterServiceHandler implements PulsarHandler {

    private PulsarFetcherConfig config;
    private IMeterProcessService processService;

    public MeterServiceHandler(ModuleManager manager, PulsarFetcherConfig config) {
        this.config = config;
        this.processService = manager.find(AnalyzerModule.NAME).provider().getService(IMeterProcessService.class);
    }

    @Override
    public void handle(final Message msg) {
        try {
            MeterDataCollection meterDataCollection = MeterDataCollection.parseFrom(msg.getData());

            MeterProcessor processor = processService.createProcessor();
            meterDataCollection.getMeterDataList().forEach(meterData -> processor.read(meterData));
            processor.process();

        } catch (Exception e) {
            log.error("handle record failed", e);
        }
    }

    @Override
    public String getTopic() {
        return "persistent://" + config.getTenant() + "/" + config.getNamespace() + "/" + config.getTopicNameOfMeters();
    }

}
