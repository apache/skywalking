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

package org.apache.skywalking.apm.testcase.thrift.server.service;

import org.apache.skywalking.apm.testcase.thrift.protocol.GreeterService;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;

public interface IServer {
    void start() throws Exception;

    void close() throws Exception;

    final class AsyncHandler implements GreeterService.AsyncIface {

        @Override
        public void echo(final String message, final AsyncMethodCallback<String> resultHandler) throws TException {
            resultHandler.onComplete("echo async: " + message);
        }
    }

    final class Handler implements GreeterService.Iface {

        @Override
        public String echo(final String message) throws TException {
            return "echo sync: " + message;
        }
    }
}
