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

package org.apache.skywalking.apm.plugin.rabbitmq;

import com.rabbitmq.client.BlockedCallback;
import com.rabbitmq.client.BlockedListener;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ExceptionHandler;
import com.rabbitmq.client.ShutdownListener;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.client.UnblockedCallback;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class RabbitMQProducerAndConsumerConstructorInterceptorTest {

    private RabbitMQProducerAndConsumerConstructorInterceptor rabbitMQProducerAndConsumerConstructorInterceptor;

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

    public class TestConnection implements Connection {

        @Override
        public InetAddress getAddress() {
            try {
                return InetAddress.getByName("127.0.0.1");
            } catch (UnknownHostException e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        public int getPort() {
            return 5672;
        }

        @Override
        public int getChannelMax() {
            return 0;
        }

        @Override
        public int getFrameMax() {
            return 0;
        }

        @Override
        public int getHeartbeat() {
            return 0;
        }

        @Override
        public Map<String, Object> getClientProperties() {
            return null;
        }

        @Override
        public String getClientProvidedName() {
            return null;
        }

        @Override
        public Map<String, Object> getServerProperties() {
            return null;
        }

        @Override
        public Channel createChannel() throws IOException {
            return null;
        }

        @Override
        public Channel createChannel(int i) throws IOException {
            return null;
        }

        @Override
        public void close() throws IOException {

        }

        @Override
        public void close(int i, String s) throws IOException {

        }

        @Override
        public void close(int i) throws IOException {

        }

        @Override
        public void close(int i, String s, int i1) throws IOException {

        }

        @Override
        public void abort() {

        }

        @Override
        public void abort(int i, String s) {

        }

        @Override
        public void abort(int i) {

        }

        @Override
        public void abort(int i, String s, int i1) {

        }

        @Override
        public void addBlockedListener(BlockedListener blockedListener) {

        }

        @Override
        public BlockedListener addBlockedListener(BlockedCallback blockedCallback,
            UnblockedCallback unblockedCallback) {
            return null;
        }

        @Override
        public boolean removeBlockedListener(BlockedListener blockedListener) {
            return false;
        }

        @Override
        public void clearBlockedListeners() {

        }

        @Override
        public ExceptionHandler getExceptionHandler() {
            return null;
        }

        @Override
        public String getId() {
            return null;
        }

        @Override
        public void setId(String s) {

        }

        @Override
        public void addShutdownListener(ShutdownListener shutdownListener) {

        }

        @Override
        public void removeShutdownListener(ShutdownListener shutdownListener) {

        }

        @Override
        public ShutdownSignalException getCloseReason() {
            return null;
        }

        @Override
        public void notifyListeners() {

        }

        @Override
        public boolean isOpen() {
            return false;
        }
    }

    private Connection testConnection;

    @Before
    public void setUp() throws Exception {
        testConnection = new TestConnection();

    }

    @Test
    public void TestRabbitMQConsumerAndProducerConstructorInterceptor() {
        rabbitMQProducerAndConsumerConstructorInterceptor = new RabbitMQProducerAndConsumerConstructorInterceptor();
        rabbitMQProducerAndConsumerConstructorInterceptor.onConstruct(enhancedInstance, new Object[] {testConnection});
        assertThat((String) enhancedInstance.getSkyWalkingDynamicField(), is("127.0.0.1:5672"));
    }
}
