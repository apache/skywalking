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

package org.apache.skywalking.oap.server.receiver.zabbix.provider.protocol;

import com.google.common.base.Charsets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.skywalking.oap.server.receiver.zabbix.provider.protocol.bean.ZabbixRequest;
import org.apache.skywalking.oap.server.receiver.zabbix.provider.protocol.bean.ZabbixRequestJsonDeserializer;

import java.util.List;

@Slf4j
public class ZabbixProtocolDecoder extends ByteToMessageDecoder {
    private static final int HEADER_LEN = 9;
    private static final byte[] PROTOCOL = new byte[] {'Z', 'B', 'X', 'D'};

    private final Gson requestParser = new GsonBuilder()
        .registerTypeAdapter(ZabbixRequest.class, new ZabbixRequestJsonDeserializer()).create();

    @Override
    protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf, List<Object> list) throws Exception {
        // Decode header and get payload
        String payload = decodeToPayload(channelHandlerContext, byteBuf);
        if (StringUtils.isEmpty(payload)) {
            return;
        }

        // Parse content and add to list
        try {
            ZabbixRequest request = requestParser.fromJson(payload, ZabbixRequest.class);
            list.add(request);
        } catch (Exception e) {
            errorProtocol(channelHandlerContext, byteBuf, "Parsing zabbix request data error");
        }
    }

    /**
     * Decode protocol to payload string
     */
    public String decodeToPayload(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf) throws InterruptedException {
        int readable = byteBuf.readableBytes();
        int baseIndex = byteBuf.readerIndex();
        if (readable < HEADER_LEN) {
            errorProtocol(channelHandlerContext, byteBuf, "header length is not enough");
            return null;
        }

        // Read header
        byte[] header = new byte[HEADER_LEN];
        byteBuf.readBytes(header);
        if (header[0] != PROTOCOL[0] || header[1] != PROTOCOL[1] || header[2] != PROTOCOL[2] || header[3] != PROTOCOL[3]) {
            errorProtocol(channelHandlerContext, byteBuf, "header is not right");
            return null;
        }

        // Only support communications protocol
        if (header[4] != 1) {
            errorProtocol(channelHandlerContext, byteBuf, "header flags only support communications protocol");
            return null;
        }

        // Check payload
        int dataLength = header[5] & 0xFF | (header[6] & 0xFF) << 8 | (header[7] & 0xFF) << 16 | (header[8] & 0xFF) << 24;
        int totalLength = HEADER_LEN + dataLength + 4;
        // If not receive all data, reset buffer and re-decode after content receive finish
        if (readable < totalLength) {
            byteBuf.readerIndex(baseIndex);
            return null;
        }

        if (dataLength <= 0) {
            errorProtocol(channelHandlerContext, byteBuf, "content could not be empty");
            return null;
        }

        // Skip protocol extensions
        byteBuf.skipBytes(4);

        // Reading content
        byte[] payload = new byte[dataLength];
        byteBuf.readBytes(payload);

        return new String(payload, Charsets.UTF_8);
    }

    /**
     * Close connection if protocol error
     */
    private void errorProtocol(ChannelHandlerContext context, ByteBuf byteBuf, String reason) throws InterruptedException {
        log.warn("Receive message is not Zabbix protocol, reason: {}", reason);
        // Skip all content
        byteBuf.skipBytes(byteBuf.readableBytes());
        // Close connection
        context.close();
    }

}
