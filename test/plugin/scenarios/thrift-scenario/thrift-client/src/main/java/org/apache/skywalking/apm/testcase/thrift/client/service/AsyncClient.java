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

package org.apache.skywalking.apm.testcase.thrift.client.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.skywalking.apm.testcase.thrift.protocol.GreeterService;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.async.TAsyncClientManager;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.transport.TNonblockingSocket;
import org.apache.thrift.transport.TTransportException;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class AsyncClient implements IClient {
    private static final Logger LOGGER = LogManager.getLogger(AsyncClient.class);

    private final TNonblockingSocket transport;
    private final GreeterService.AsyncClient client;

    public AsyncClient(final int port) throws IOException {
        this.transport = new TNonblockingSocket("localhost", port);
        this.client = new GreeterService.AsyncClient(
            TCompactProtocol::new,
            new TAsyncClientManager(),
            this.transport
        );
    }

    @Override
    public void start() throws TTransportException {

    }

    @Override
    public String echo(final String message) throws TException {
        final CountDownLatch latch = new CountDownLatch(1);

        AtomicReference<String> resp = new AtomicReference<>();
        client.echo(message, new AsyncMethodCallback<String>() {
            @Override
            public void onComplete(final String response) {
                resp.set(response);
                latch.countDown();
            }

            @Override
            public void onError(final Exception exception) {
                latch.countDown();
                LOGGER.error("", exception);
            }
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            LOGGER.error("", e);
        }
        return resp.get();
    }

    @Override
    public void close() {
        transport.close();
    }
}
