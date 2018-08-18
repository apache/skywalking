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


package org.apache.skywalking.apm.collector.agent.jetty.provider.handler.naming;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import org.apache.skywalking.apm.collector.server.jetty.ArgumentsParseException;
import org.apache.skywalking.apm.collector.server.jetty.JettyJsonHandler;

/**
 * @author peng-yongsheng
 */
public class AgentJettyNamingHandler extends JettyJsonHandler {

    private final AgentJettyNamingListener namingListener;

    public AgentJettyNamingHandler(AgentJettyNamingListener namingListener) {
        this.namingListener = namingListener;
    }

    @Override public String pathSpec() {
        return "/agent/jetty";
    }

    @Override protected JsonElement doGet(HttpServletRequest req) throws ArgumentsParseException {
        Set<String> servers = namingListener.getAddresses();
        JsonArray serverArray = new JsonArray();
        servers.forEach(serverArray::add);
        return serverArray;
    }

    @Override protected JsonElement doPost(HttpServletRequest req) throws ArgumentsParseException {
        throw new UnsupportedOperationException();
    }
}
