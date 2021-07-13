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

package org.apache.skywalking.apm.testcase.thrift.client.controller;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.skywalking.apm.testcase.thrift.client.service.AsyncClient;
import org.apache.skywalking.apm.testcase.thrift.client.service.IClient;
import org.apache.skywalking.apm.testcase.thrift.client.service.SyncClient;
import org.apache.thrift.TException;
import org.apache.thrift.transport.TTransportException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/case")
public class CaseController {
    private static final Logger LOGGER = LogManager.getLogger(CaseController.class);

    private static final String SUCCESS = "Success";
    private IClient async;
    private IClient sync;

    private IClient hasync;

    private final AtomicInteger status = new AtomicInteger(0);
    private final CountDownLatch initialized = new CountDownLatch(1);

    @RequestMapping("/thrift-scenario")
    @ResponseBody
    public String testcase() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(3);

        call(async, latch);
        call(sync, latch);

        latch.await();
        return SUCCESS;
    }

    @RequestMapping("/healthCheck")
    @ResponseBody
    public String healthCheck() throws IOException, TTransportException {
        if (status.compareAndSet(0, 1)) {
            async = new AsyncClient(9091);
            sync = new SyncClient(9090);
            async.start();
            sync.start();

            initialized.countDown();
        }
        try {
            initialized.await(2000, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new IOException("pending");
        }
        return SUCCESS;
    }

    private void call(IClient client, CountDownLatch latch) {
        new Thread(() -> {
            try {
                client.echo("skywalking");
            } catch (TException e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        }).start();
    }

}