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

package org.apache.skywalking.apm.collector.agent.jetty.provider;

import com.google.gson.JsonElement;
import java.io.IOException;

/**
 * @author peng-yongsheng
 */
public class SegmentPost {

    public static void main(String[] args) throws IOException {
        ApplicationRegisterPost applicationRegisterPost = new ApplicationRegisterPost();
        applicationRegisterPost.send("json/application-register-consumer.json");
        applicationRegisterPost.send("json/application-register-provider.json");

        InstanceRegisterPost instanceRegisterPost = new InstanceRegisterPost();
        instanceRegisterPost.send("json/instance-register-consumer.json");
        instanceRegisterPost.send("json/instance-register-provider.json");

        ServiceNameRegisterPost serviceNameRegisterPost = new ServiceNameRegisterPost();
        serviceNameRegisterPost.send("json/servicename-register-consumer.json");
        serviceNameRegisterPost.send("json/servicename-register-provider.json");

        JsonElement provider = JsonFileReader.INSTANCE.read("json/dubbox-provider.json");
        JsonElement consumer = JsonFileReader.INSTANCE.read("json/dubbox-consumer.json");

        for (int i = 0; i < 1; i++) {
            HttpClientTools.INSTANCE.post("http://localhost:12800/segments", provider.toString());
            HttpClientTools.INSTANCE.post("http://localhost:12800/segments", consumer.toString());
        }
    }
}
