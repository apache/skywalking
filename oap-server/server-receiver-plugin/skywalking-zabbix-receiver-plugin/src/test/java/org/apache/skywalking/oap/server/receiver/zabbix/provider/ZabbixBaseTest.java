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

package org.apache.skywalking.oap.server.receiver.zabbix.provider;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import lombok.Getter;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.receiver.zabbix.provider.protocol.ZabbixProtocolDecoder;
import org.apache.skywalking.oap.server.receiver.zabbix.provider.protocol.ZabbixProtocolEncoder;
import org.apache.skywalking.oap.server.receiver.zabbix.provider.protocol.ZabbixProtocolHandler;
import org.apache.skywalking.oap.server.receiver.zabbix.provider.protocol.bean.ZabbixProtocolType;
import org.apache.skywalking.oap.server.receiver.zabbix.provider.protocol.bean.ZabbixRequest;
import org.apache.skywalking.oap.server.receiver.zabbix.provider.protocol.bean.ZabbixResponse;
import org.junit.Assert;
import org.junit.Before;
import org.mockito.Spy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public abstract class ZabbixBaseTest {

    @Spy
    private ChannelHandlerContext channelHandlerContext;

    private List<Object> requests;
    private List<Object> responses;

    private ZabbixProtocolEncoderWrapper encoder;
    private ZabbixProtocolDecoderWrapper decoder;
    private ZabbixProtocolHandler handler;
    protected ZabbixMetrics zabbixMetrics;

    /**
     * Customize the Zabbix metrics
     */
    protected abstract ZabbixMetrics buildZabbixMetrics() throws Exception;

    @Before
    public void setupMetrics() throws Throwable {
        zabbixMetrics = buildZabbixMetrics();
        requests = new ArrayList<>();
        responses = new ArrayList<>();

        encoder = new ZabbixProtocolEncoderWrapper();
        decoder = new ZabbixProtocolDecoderWrapper();
        handler = new ZabbixProtocolHandler(zabbixMetrics);
        when(channelHandlerContext.writeAndFlush(any())).thenAnswer(invocationOnMock -> {
            responses.add(invocationOnMock.getArgument(0));
            return null;
        });
        ByteBufAllocator allocator = mock(ByteBufAllocator.class);
        when(allocator.buffer(anyInt())).thenAnswer(invocationOnMock -> Unpooled.buffer(invocationOnMock.getArgument(0)));
        when(channelHandlerContext.alloc()).thenReturn(allocator);
    }

    /**
     * Verify request error protocol
     */
    public void assertWriteErrorProtocol(byte[] data) throws Throwable {
        ZabbixProtocolDecoderWrapper decoder = new ZabbixProtocolDecoderWrapper();
        decoder.decode(null, Unpooled.wrappedBuffer(data), null);
        if (!decoder.isProtocolError()) {
            throw new IllegalStateException("Could not detect need more input error");
        }
    }

    /**
     * Assert need more input to server
     */
    public void assertNeedMoreInput(byte[] data) throws Throwable {
        ZabbixProtocolDecoder decoder = spy(new ZabbixProtocolDecoder());
        if (decoder.decodeToPayload(null, Unpooled.wrappedBuffer(data)) != null) {
            throw new IllegalStateException("Could not detect need more input error");
        }
    }

    /**
     * Verify Active checks item names
     */
    public void assertZabbixActiveChecksResponse(int inx, String... itemNames) throws Exception {
        ZabbixResponse response = (ZabbixResponse) responses.get(inx);

        // Active Checks
        Assert.assertEquals(itemNames.length, response.getActiveChecks().size());
        for (String itemName : itemNames) {
            boolean found = false;

            for (final ZabbixResponse.ActiveChecks checks : response.getActiveChecks()) {
                if (Objects.equals(checks.getKey(), itemName)) {
                    Assert.assertTrue(checks.getDelay() > 0);
                    Assert.assertTrue(checks.getLastlogsize() >= 0);
                    Assert.assertTrue(checks.getMtime() >= 0);
                    found = true;
                }
            }

            if (!found) {
                throw new AssertionError("Could not found " + itemName + " in Active Checks response");
            }
        }

        encoder.encode(channelHandlerContext, response, null);
        String respBody = decoder.decodeToPayload(channelHandlerContext, (ByteBuf) responses.get(inx + 1));
        assertZabbixActiveChecksResponseWithEncoded(respBody, itemNames);
    }

    /**
     * Verify Active checks item names with encoded
     */
    private void assertZabbixActiveChecksResponseWithEncoded(String body, String... itemNames) {
        Assert.assertNotNull(body);
        JsonElement bodyRoot = new Gson().fromJson(body, JsonElement.class);
        JsonObject rootObject = bodyRoot.getAsJsonObject();
        // Basic response status
        Assert.assertEquals("success", rootObject.get("response").getAsString());

        // Active Checks
        Assert.assertNotNull(rootObject.get("data"));
        JsonArray activeChecks = rootObject.getAsJsonArray("data");
        Assert.assertEquals(itemNames.length, activeChecks.size());
        for (String itemName : itemNames) {
            boolean found = false;

            for (JsonElement perCheck : activeChecks) {
                JsonObject curCheck = perCheck.getAsJsonObject();
                String itemKey = curCheck.get("key").getAsString();
                if (Objects.equals(itemKey, itemName)) {
                    Assert.assertTrue(curCheck.get("delay").getAsInt() > 0);
                    Assert.assertTrue(curCheck.get("lastlogsize").getAsInt() >= 0);
                    Assert.assertTrue(curCheck.get("mtime").getAsInt() >= 0);
                    found = true;
                }
            }

            if (!found) {
                throw new AssertionError("Could not found " + itemName + " in Active Checks response");
            }
        }
    }

    /**
     * Verify Zabbix agent data response
     */
    public void assertZabbixAgentDataResponse(int inx) throws Exception {
        ZabbixResponse response = (ZabbixResponse) responses.get(inx);

        // Agent data info
        Assert.assertTrue(StringUtil.isNotEmpty(response.getAgentData().getInfo()));

        encoder.encode(channelHandlerContext, response, null);
        String respBody = decoder.decodeToPayload(channelHandlerContext, (ByteBuf) responses.get(inx + 1));
        assertZabbixAgentDataResponseWithEncoded(respBody);
    }

    /**
     * Verify Zabbix agent data response with encoded
     */
    public void assertZabbixAgentDataResponseWithEncoded(String body) {
        Assert.assertNotNull(body);
        JsonElement bodyRoot = new Gson().fromJson(body, JsonElement.class);
        JsonObject rootObject = bodyRoot.getAsJsonObject();
        // Basic response status
        Assert.assertEquals("success", rootObject.get("response").getAsString());

        // Agent data
        Assert.assertNotNull(rootObject.get("info"));
    }

    /**
     * Verify Zabbix Active Checks request data
     */
    public void assertZabbixActiveChecksRequest(int inx, String hostName) {
        ZabbixRequest request = assertZabbixRequestBasic(inx, ZabbixProtocolType.ACTIVE_CHECKS);
        Assert.assertNotNull(request.getActiveChecks());
        Assert.assertEquals(hostName, request.getActiveChecks().getHostName());
    }

    /**
     * Verify Zabbix Agent data request data
     */
    public void assertZabbixAgentDataRequest(int inx, String hostName, String... keyNames) {
        ZabbixRequest request = assertZabbixRequestBasic(inx, ZabbixProtocolType.AGENT_DATA);
        List<ZabbixRequest.AgentData> agentDataList = request.getAgentDataList();
        Assert.assertNotNull(agentDataList);
        Assert.assertEquals(keyNames.length, agentDataList.size());
        for (String keyName : keyNames) {
            boolean found = false;

            for (ZabbixRequest.AgentData agentData : agentDataList) {
                if (Objects.equals(keyName, agentData.getKey())) {
                    Assert.assertEquals(hostName, agentData.getHost());
                    Assert.assertTrue(NumberUtils.isParsable(agentData.getValue()) ?
                        Double.parseDouble(agentData.getValue()) > 0 : StringUtil.isNotBlank(agentData.getValue()));
                    Assert.assertTrue(agentData.getId() > 0);
                    Assert.assertTrue(agentData.getClock() > 0);
                    Assert.assertTrue(agentData.getNs() > 0);
                    Assert.assertTrue(agentData.getState() == 0);
                    found = true;
                }
            }

            if (!found) {
                // Throw exception when key not found
                throw new AssertionError("Could not found " + keyName + " in Agent data request");
            }
        }
    }

    /**
     * Verify zabbix request basic info
     */
    private ZabbixRequest assertZabbixRequestBasic(int inx, ZabbixProtocolType protocolType) {
        Assert.assertNotNull(requests);
        Assert.assertTrue(requests.size() > inx);
        ZabbixRequest request = (ZabbixRequest) requests.get(inx);
        Assert.assertEquals(protocolType, request.getType());
        return request;
    }

    public byte[] buildZabbixRequestData(String content) {
        // Build header
        byte[] payload = content.getBytes();
        int payloadLength = payload.length;
        byte[] header = new byte[] {
            'Z', 'B', 'X', 'D', '\1',
            (byte) (payloadLength & 0xFF),
            (byte) (payloadLength >> 8 & 0xFF),
            (byte) (payloadLength >> 16 & 0xFF),
            (byte) (payloadLength >> 24 & 0xFF),
            '\0', '\0', '\0', '\0'};

        byte[] packet = new byte[header.length + payloadLength];
        System.arraycopy(header, 0, packet, 0, header.length);
        System.arraycopy(payload, 0, packet, header.length, payloadLength);

        return packet;
    }

    public void writeZabbixMessage(String message) throws Exception {
        ArrayList<Object> data = new ArrayList<>();
        decoder.decode(channelHandlerContext, Unpooled.wrappedBuffer(buildZabbixRequestData(message)), data);
        requests.add(data.get(0));

        handler.channelRead0(channelHandlerContext, (ZabbixRequest) data.get(0));
    }

    @Getter
    private class ZabbixProtocolDecoderWrapper extends ZabbixProtocolDecoder {
        private boolean protocolError;

        @Override
        public void decode(final ChannelHandlerContext channelHandlerContext,
                              final ByteBuf byteBuf,
                              final List<Object> list) throws Exception {
            super.decode(channelHandlerContext, byteBuf, list);
        }

        @Override
        protected void errorProtocol(final ChannelHandlerContext context,
                                     final ByteBuf byteBuf,
                                     final String reason,
                                     final Throwable ex) throws InterruptedException {
            protocolError = true;
        }
    }

    @Getter
    private class ZabbixProtocolEncoderWrapper extends ZabbixProtocolEncoder {
        @Override
        public void encode(final ChannelHandlerContext channelHandlerContext,
                              final ZabbixResponse zabbixResponse,
                              final List<Object> list) throws Exception {
            super.encode(channelHandlerContext, zabbixResponse, list);
        }
    }

}
