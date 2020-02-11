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
 */

package org.apache.skywalking.apm.testcase.netty.socketio;

import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketIOServer;
import io.netty.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class SocketIOStarter {

    public static final Integer SERVER_PORT = 9092;
    public static final String LISTEN_EVENT_NAME = "send_data";
    public static final String SEND_EVENT_NAME = "get_data";

    private SocketIOServer server;
    private Future<Void> startFuture;
    private static final SocketIOStarter INSTANCE = new SocketIOStarter();

    public static final SocketIOStarter getInstance() {
        return INSTANCE;
    }

    public SocketIOStarter() {
        Configuration config = new Configuration();
        config.setHostname("localhost");
        config.setPort(SERVER_PORT);
        config.setBossThreads(1);
        config.setWorkerThreads(1);

        server = new SocketIOServer(config);
        startFuture = server.startAsync();
    }

    public boolean healthCheck() throws InterruptedException {
        return startFuture.await(1L, TimeUnit.SECONDS);
    }

    public void sendEvent(String message) {
        server.getAllClients().forEach(e -> e.sendEvent(SEND_EVENT_NAME, message));
    }

}
