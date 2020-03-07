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

package org.apache.skywalking.apm.plugin.activemq;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQSession;
import org.apache.activemq.command.SessionId;
import org.apache.activemq.management.JMSStatsImpl;
import org.apache.activemq.transport.FutureResponse;
import org.apache.activemq.transport.ResponseCallback;
import org.apache.activemq.transport.Transport;
import org.apache.activemq.transport.TransportListener;
import org.apache.activemq.util.IdGenerator;
import org.apache.activemq.wireformat.WireFormat;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import javax.jms.JMSException;
import java.io.IOException;
import java.net.URI;
import java.security.cert.X509Certificate;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class ActiveMQConsumerAndProducerConstructorInterceptorTest {

    @Mock
    private ActiveMQConnection activeMQConnection;

    private IdGenerator idGenerator;

    private JMSStatsImpl jmsStats;

    @Mock
    private ActiveMQSession activeMQSession;

    private SessionId sessionId;

    public class TransportTest implements Transport {

        private String remoteAddress;

        @Override
        public void oneway(Object o) throws IOException {

        }

        @Override
        public FutureResponse asyncRequest(Object o, ResponseCallback responseCallback) throws IOException {
            return null;
        }

        @Override
        public Object request(Object o) throws IOException {
            return null;
        }

        @Override
        public Object request(Object o, int i) throws IOException {
            return null;
        }

        @Override
        public TransportListener getTransportListener() {
            return null;
        }

        @Override
        public void setTransportListener(TransportListener transportListener) {

        }

        @Override
        public <T> T narrow(Class<T> aClass) {
            return null;
        }

        @Override
        public String getRemoteAddress() {
            return this.remoteAddress;
        }

        public String setRemoteAddress(String remoteAddress) {
            return this.remoteAddress = remoteAddress;
        }

        @Override
        public boolean isFaultTolerant() {
            return false;
        }

        @Override
        public boolean isDisposed() {
            return false;
        }

        @Override
        public boolean isConnected() {
            return false;
        }

        @Override
        public boolean isReconnectSupported() {
            return false;
        }

        @Override
        public boolean isUpdateURIsSupported() {
            return false;
        }

        @Override
        public void reconnect(URI uri) throws IOException {

        }

        @Override
        public void updateURIs(boolean b, URI[] uris) throws IOException {

        }

        @Override
        public int getReceiveCounter() {
            return 0;
        }

        @Override
        public X509Certificate[] getPeerCertificates() {
            return new X509Certificate[0];
        }

        @Override
        public void setPeerCertificates(X509Certificate[] x509Certificates) {

        }

        @Override
        public WireFormat getWireFormat() {
            return null;
        }

        @Override
        public void start() throws Exception {

        }

        @Override
        public void stop() throws Exception {

        }
    }

    public class TestConnection extends ActiveMQConnection {

        public TestConnection(Transport transport, IdGenerator clientIdGenerator, IdGenerator connectionIdGenerator,
            JMSStatsImpl factoryStats) throws Exception {
            super(transport, clientIdGenerator, connectionIdGenerator, factoryStats);
        }
    }

    private class TestActiveMQSession extends ActiveMQSession {

        public TestActiveMQSession(ActiveMQConnection connection, SessionId sessionId, int acknowledgeMode,
            boolean asyncDispatch, boolean sessionAsyncDispatch) throws JMSException {
            super(connection, sessionId, acknowledgeMode, asyncDispatch, sessionAsyncDispatch);
        }
    }

    private ActiveMQConsumerConstructorInterceptor activeMQConsumerAndProducerConstructorInterceptor;

    private EnhancedInstance enhancedInstance = new EnhancedInstance() {
        private String test;

        @Override
        public Object getSkyWalkingDynamicField() {
            return test;
        }

        @Override
        public void setSkyWalkingDynamicField(Object value) {
            test = (String) value;
        }
    };

    @Before
    public void setUp() throws Exception {
        TransportTest transport = new TransportTest();
        transport.setRemoteAddress("tcp://127.0.0.1:61616");
        idGenerator = new IdGenerator("aaa");
        jmsStats = new JMSStatsImpl();
        activeMQConnection = new TestConnection(transport, idGenerator, idGenerator, jmsStats);
        sessionId = new SessionId();
        activeMQSession = new TestActiveMQSession(activeMQConnection, sessionId, 1, true, true);
    }

    @Test
    public void TestActiveMQConsumerAndProducerConstructorInterceptor() {
        activeMQConsumerAndProducerConstructorInterceptor = new ActiveMQConsumerConstructorInterceptor();
        activeMQConsumerAndProducerConstructorInterceptor.onConstruct(enhancedInstance, new Object[] {activeMQSession});
        assertThat((String) enhancedInstance.getSkyWalkingDynamicField(), is("127.0.0.1:61616"));
    }
}