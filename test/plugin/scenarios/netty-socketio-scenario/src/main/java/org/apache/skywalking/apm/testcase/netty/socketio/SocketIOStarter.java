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
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

import java.net.URISyntaxException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * @author MrPro
 */
public class SocketIOStarter {

    public static final Integer SERVER_PORT = 9092;
    public static final String LISTEN_EVENT_NAME = "send_data";
    public static final String SEND_EVENT_NAME = "get_data";

    public static SocketIOServer server;
    public static Socket client;

    private static CountDownLatch connectedCountDownLatch = new CountDownLatch(1);

    public static void startServer() {
        if (server != null) {
            return;
        }
        Configuration config = new Configuration();
        config.setHostname("localhost");
        config.setPort(SERVER_PORT);
        config.setBossThreads(1);
        config.setWorkerThreads(1);

        server = new SocketIOServer(config);

        server.start();
    }

    public static void startClientAndWaitConnect() throws URISyntaxException, InterruptedException {
        if (client != null) {
            // check client is connected again
            // if this method invoke on multi thread, client will return but not connected
            connectedCountDownLatch.await(5, TimeUnit.SECONDS);
            return;
        }
        client = IO.socket("http://localhost:" + SERVER_PORT);
        LinkedBlockingQueue<Boolean> connected = new LinkedBlockingQueue<>(1);
        client.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... objects) {
                connectedCountDownLatch.countDown();
            }
        });
        client.connect();

        // wait connect to server
        connectedCountDownLatch.await(5, TimeUnit.SECONDS);
    }

}
