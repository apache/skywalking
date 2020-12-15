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

package org.apache.skywalking.apm.testcase.baidu.brpc.service;

import com.baidu.brpc.RpcContext;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.skywalking.apm.testcase.baidu.brpc.interfaces.Echo;
import org.apache.skywalking.apm.testcase.baidu.brpc.interfaces.EchoService;

/**
 * Copy from brpc-java-example
 */
public class EchoServiceImpl implements EchoService {

    @Override
    public Echo.EchoResponse echo(Echo.EchoRequest request) {
        if (RpcContext.isSet()) {
            RpcContext rpcContext = RpcContext.getContext();
            ByteBuf attachment = rpcContext.getRequestBinaryAttachment();
            if (attachment != null) {
                rpcContext.setResponseBinaryAttachment(Unpooled.copiedBuffer(attachment));
            }
        }
        String message = request.getMessage();
        Echo.EchoResponse response = Echo.EchoResponse.newBuilder()
                .setMessage(message).build();
        return response;
    }
}
