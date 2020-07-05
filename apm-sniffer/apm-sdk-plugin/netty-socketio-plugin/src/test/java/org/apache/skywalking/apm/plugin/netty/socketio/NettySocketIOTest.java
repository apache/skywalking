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

package org.apache.skywalking.apm.plugin.netty.socketio;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.handler.ClientHead;
import com.corundumstudio.socketio.namespace.Namespace;
import com.corundumstudio.socketio.protocol.Packet;
import com.corundumstudio.socketio.transport.NamespaceClient;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
import org.powermock.reflect.Whitebox;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class NettySocketIOTest {

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    private NettySocketIOConnectionInterceptor connectionInterceptor;
    private NettySocketIOOnEventInterceptor onEventInterceptor;
    private NettySocketIORoomInterceptor roomInterceptor;
    private NettySocketIOConstructorInterceptor constructorInterceptor;

    @Mock
    private SocketIOClient socketIOClient;
    @Mock
    private Packet sendPacket;
    @Mock
    private ClientHead clientHead;
    @Mock
    private Namespace namespace;

    private Method connectOnConnectMethod;
    private Method connectOnDisConnectMethod;
    private Method roomLeaveMethod;
    private Method roomJoinMethod;

    private NettySocketIOClientInfo socketIOClientInfo = new NettySocketIOClientInfo(null, null, "127.0.0.1:0");

    private EnhancedInstance enhancedInstance = new EnhancedInstance() {
        @Override
        public Object getSkyWalkingDynamicField() {
            return socketIOClientInfo;
        }

        @Override
        public void setSkyWalkingDynamicField(Object value) {

        }
    };

    @Before
    public void setUp() {
        InetSocketAddress addr = new InetSocketAddress("127.0.0.1", 0);
        when(socketIOClient.getRemoteAddress()).thenReturn(addr);
        when(sendPacket.getName()).thenReturn("test");
        when(clientHead.getRemoteAddress()).thenReturn(addr);

        connectionInterceptor = new NettySocketIOConnectionInterceptor();
        onEventInterceptor = new NettySocketIOOnEventInterceptor();
        roomInterceptor = new NettySocketIORoomInterceptor();
        constructorInterceptor = new NettySocketIOConstructorInterceptor();

        // work for connection
        connectOnConnectMethod = Whitebox.getMethods(Namespace.class, "onConnect")[0];
        connectOnDisConnectMethod = Whitebox.getMethods(Namespace.class, "onDisconnect")[0];

        // work for room
        roomJoinMethod = Whitebox.getMethods(NamespaceClient.class, "joinRoom")[0];
        roomLeaveMethod = Whitebox.getMethods(NamespaceClient.class, "leaveRoom")[0];
    }

    @Test
    public void assertConnection() throws Throwable {
        connectionInterceptor.beforeMethod(null, connectOnConnectMethod, new Object[] {socketIOClient}, null, null);
        connectionInterceptor.afterMethod(null, connectOnConnectMethod, new Object[] {socketIOClient}, null, null);

        connectionInterceptor.beforeMethod(null, connectOnDisConnectMethod, new Object[] {socketIOClient}, null, null);
        connectionInterceptor.afterMethod(null, connectOnDisConnectMethod, new Object[] {socketIOClient}, null, null);

        List<TraceSegment> traceSegments = segmentStorage.getTraceSegments();
        Assert.assertThat(traceSegments.size(), is(2));
    }

    @Test
    public void assertOnEvent() throws Throwable {
        onEventInterceptor.beforeMethod(null, null, new Object[] {
            null,
            "test"
        }, null, null);
        onEventInterceptor.afterMethod(null, null, new Object[] {
            null,
            "test"
        }, null, null);

        List<TraceSegment> traceSegments = segmentStorage.getTraceSegments();
        Assert.assertThat(traceSegments.size(), is(1));
    }

    @Test
    public void assertConstructor() throws Throwable {
        constructorInterceptor.onConstruct(enhancedInstance, new Object[] {
            clientHead,
            namespace
        });
    }

    @Test
    public void assertRoom() throws Throwable {
        roomInterceptor.beforeMethod(null, roomJoinMethod, new Object[] {"test_room"}, null, null);
        roomInterceptor.afterMethod(null, roomJoinMethod, new Object[] {"test_room"}, null, null);

        roomInterceptor.beforeMethod(null, roomLeaveMethod, new Object[] {"test_room"}, null, null);
        roomInterceptor.afterMethod(null, roomLeaveMethod, new Object[] {"test_room"}, null, null);

        List<TraceSegment> traceSegments = segmentStorage.getTraceSegments();
        Assert.assertThat(traceSegments.size(), is(2));
    }

}
