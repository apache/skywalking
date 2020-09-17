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

import org.apache.skywalking.apm.testcase.thrift.protocol.GreeterService;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;

public class SyncClient implements IClient {
    private final TTransport transport;
    private final GreeterService.Client client;

    public SyncClient(TTransport transport) {
        this.transport = transport;
        client = new GreeterService.Client(new TCompactProtocol(transport));
    }

    @Override
    public void start() throws TTransportException {
        transport.open();
    }

    @Override
    public void close() {
        transport.close();
    }


    @Override
    public String echo(String message) throws TException {
        return null;
    }
}
