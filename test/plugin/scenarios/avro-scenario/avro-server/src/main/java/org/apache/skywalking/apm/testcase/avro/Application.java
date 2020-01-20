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
package org.apache.skywalking.apm.testcase.avro;

import example.proto.Greeter;
import example.proto.Message;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.avro.AvroRemoteException;
import org.apache.avro.ipc.HttpServer;
import org.apache.avro.ipc.NettyServer;
import org.apache.avro.ipc.specific.SpecificResponder;
import org.apache.avro.util.Utf8;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Application {
    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    public static class GreeterImpl implements Greeter {
        @Override public CharSequence hello(Message message) throws AvroRemoteException {
            System.out.println(message);
            return new Utf8("success");
        }
    }

    private static final ExecutorService executor = Executors.newFixedThreadPool(2);

    public static void main(String[] args) throws Exception {
        // For NettyServer
        executor.submit(() -> {
            SpecificResponder responder = new SpecificResponder(Greeter.class, new GreeterImpl());
            NettyServer server = new NettyServer(responder, new InetSocketAddress(9018));
            server.start();
        });

        // For HttpServer
        executor.submit(() -> {
            try {
                SpecificResponder responder = new SpecificResponder(Greeter.class, new GreeterImpl());
                HttpServer server = new HttpServer(responder, 9019);
                server.start();
            } catch (IOException e) {
                LOG.error("", e);
            }
        });
    }
}
