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
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.SocketChannel;
import java.net.SocketTimeoutException;
import lombok.SneakyThrows;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.receiver.zabbix.provider.protocol.ZabbixErrorProtocolException;
import org.apache.skywalking.oap.server.receiver.zabbix.provider.protocol.ZabbixProtocolDecoder;
import org.apache.skywalking.oap.server.receiver.zabbix.provider.protocol.ZabbixProtocolHandler;
import org.apache.skywalking.oap.server.receiver.zabbix.provider.protocol.ZabbixServer;
import org.apache.skywalking.oap.server.receiver.zabbix.provider.protocol.bean.ZabbixProtocolType;
import org.apache.skywalking.oap.server.receiver.zabbix.provider.protocol.bean.ZabbixRequest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.mockito.stubbing.Answer;
import org.powermock.reflect.Whitebox;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

public abstract class ZabbixBaseTest {
    private static final String TCP_HOST = "0.0.0.0";
    private static final int TCP_PORT = 10051;

    protected ZabbixServer zabbixServer;
    protected SocketClient socketClient;
    protected ZabbixMetrics zabbixMetrics;

    /**
     * Customize the Zabbix metrics
     */
    protected abstract ZabbixMetrics buildZabbixMetrics() throws Exception;

    @Before
    public void setupService() throws Throwable {
        // Startup server
        ZabbixModuleConfig config = new ZabbixModuleConfig();
        config.setPort(TCP_PORT);
        config.setHost(TCP_HOST);
        zabbixMetrics = buildZabbixMetrics();
        zabbixServer = new ZabbixServerWrapper(config, zabbixMetrics);
        zabbixServer.start();
    }

    @After
    public void cleanup() {
        zabbixServer.stop();
    }

    /**
     * Verify request error protocol
     */
    public void assertWriteErrorProtocol(byte[] data) throws Throwable {
        startupSocketClient();
        try {
            socketClient.socket.getOutputStream().write(data);

            for (int i = 0; i < 10; i++) {
                // No response
                if (socketClient.socket.getInputStream().available() == 0 && socketClient.socket.getInputStream().read() == -1) {
                    return ;
                }
                TimeUnit.MILLISECONDS.sleep(500);
            }

            throw new IllegalStateException("Could not detect protocol error");
        } finally {
            stopSocketClient();
        }
    }

    /**
     * Assert need more input to server
     */
    public void assertNeedMoreInput(byte[] data) throws Throwable {
        startupSocketClient();
        try {
            socketClient.socket.getOutputStream().write(data);

            try {
                for (int i = 0; i < 10; i++) {
                    // No response
                    if (socketClient.socket.getInputStream().available() == 0 && socketClient.socket.getInputStream().read() == -1) {
                        return ;
                    }
                    TimeUnit.MILLISECONDS.sleep(100);
                }
            } catch (SocketTimeoutException e) {
                // Read timeout mean need more content
                return;
            }

            throw new IllegalStateException("Could not detect need more input error");
        } finally {
            stopSocketClient();
        }
    }

    /**
     * Verify Active checks item names
     */
    public void assertZabbixActiveChecksResponse(String body, String... itemNames) {
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
    public void assertZabbixAgentDataResponse(String body) {
        Assert.assertNotNull(body);
        JsonElement bodyRoot = new Gson().fromJson(body, JsonElement.class);
        JsonObject rootObject = bodyRoot.getAsJsonObject();
        // Basic response status
        Assert.assertEquals("success", rootObject.get("response").getAsString());

        // Agent data info
        Assert.assertNotNull(rootObject.get("info"));
        Assert.assertTrue(StringUtil.isNotEmpty(rootObject.get("info").getAsString()));
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
        List<ZabbixRequest> requests = socketClient.requests;
        Assert.assertNotNull(requests);
        Assert.assertTrue(requests.size() > inx);
        ZabbixRequest request = requests.get(inx);
        Assert.assertEquals(protocolType, request.getType());
        return request;
    }

    /**
     * Startup a new socket client to server
     */
    protected void startupSocketClient() throws Throwable {
        socketClient = Optional.ofNullable(this.socketClient).orElseGet(SocketClient::new);
        socketClient.startup();
    }

    /**
     * Close the client
     */
    protected void stopSocketClient() {
        Optional.ofNullable(socketClient).ifPresent(SocketClient::stop);
        socketClient = null;
    }

    /**
     * Connect to receiver server
     */
    protected static class SocketClient {
        private ZabbixProtocolHandler protocolHandler;
        private Throwable spyHandlerException;
        private Socket socket;
        private List<ZabbixRequest> requests;

        private void startup() throws Throwable {
            if (socket != null) {
                return;
            }
            socket = new Socket();
            socket.setSoTimeout(2000);
            socket.connect(new InetSocketAddress(TCP_HOST, TCP_PORT));

            // Waiting for connection
            while (!socket.isConnected() || (protocolHandler == null && spyHandlerException == null)) {
                TimeUnit.SECONDS.sleep(1);
            }

            if (spyHandlerException != null) {
                throw spyHandlerException;
            }

            // Intercept message received
            requests = new ArrayList<>();
            doAnswer((Answer<Object>) invocationOnMock -> {
                requests.add(invocationOnMock.getArgument(1));
                return invocationOnMock.callRealMethod();
            }).when(protocolHandler).channelRead0(any(), any());
        }

        @SneakyThrows
        private void stop() {
            if (socket != null && socket.isConnected()) {
                socket.close();
            }
        }

        public void writeZabbixMessage(String message) throws IOException {
            this.socket.getOutputStream().write(buildZabbixRequestData(message));
        }

        public static byte[] buildZabbixRequestData(String content) {
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

        /**
         * Finding and spy the Zabbix handler
         */
        private void spyHandler(SocketChannel channel) {
            Object tailContext = Whitebox.getInternalState(channel.pipeline(), "tail");
            Object handlerContext = Whitebox.getInternalState(tailContext, "prev");
            ZabbixProtocolHandler handler = spyHandler(handlerContext, ZabbixProtocolHandler.class);
            if (handler == null) {
                throw new IllegalStateException("Unnable to find Zabbix protocol handler");
            }
            protocolHandler = handler;
        }

        private <T> T spyHandler(Object handlerContext, Class<T> handlerCls) {
            if (handlerContext == null || handlerContext.getClass().getSimpleName().contains("HeadContext")) {
                return null;
            }
            Object handler = Whitebox.getInternalState(handlerContext, "handler");
            if (handler.getClass().equals(handlerCls)) {
                Object realHandler = spy(handler);
                Whitebox.setInternalState(handlerContext, "handler", realHandler);
                return (T) realHandler;
            } else {
                return spyHandler(Whitebox.getInternalState(handlerContext, "prev"), handlerCls);
            }
        }

        private byte[] readAllContent(InputStream inputStream) throws IOException {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(512);
            byte[] buffer = new byte[512];
            int len;
            while ((len = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, len);
                if (len != buffer.length) {
                    break;
                }
            }
            return outputStream.toByteArray();
        }

        public String waitAndGetResponsePayload() throws InterruptedException, IOException, ZabbixErrorProtocolException {
            ChannelHandlerContext channelHandlerContext = mock(ChannelHandlerContext.class);
            ByteBuf byteBuf = Unpooled.copiedBuffer(readAllContent(socket.getInputStream()));
            return new ZabbixProtocolDecoder().decodeToPayload(channelHandlerContext, byteBuf);
        }
    }

    /**
     * Zabbix binder wrapper, support spy Zabbix message received data
     */
    private class ZabbixServerWrapper extends ZabbixServer {

        public ZabbixServerWrapper(ZabbixModuleConfig config, ZabbixMetrics zabbixMetrics) {
            super(config, zabbixMetrics);
        }

        @Override
        public void initChannel(SocketChannel channel) {
            super.initChannel(channel);

            try {
                socketClient.spyHandler(channel);
            } catch (Throwable e) {
                socketClient.spyHandlerException = e;
            }
        }
    }

}
