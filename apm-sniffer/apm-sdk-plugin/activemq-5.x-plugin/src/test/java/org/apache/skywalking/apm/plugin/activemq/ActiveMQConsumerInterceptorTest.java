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

import org.apache.activemq.command.ActiveMQDestination;
import org.apache.activemq.command.Message;
import org.apache.activemq.command.MessageDispatch;
import org.apache.activemq.command.Response;
import org.apache.activemq.state.CommandVisitor;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.junit.*;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
import javax.jms.JMSException;
import java.io.IOException;

import java.util.List;

import static org.hamcrest.CoreMatchers.is;

import org.junit.Before;
import org.junit.Test;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class ActiveMQConsumerInterceptorTest {

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    private ActiveMQConsumerInterceptor activeMQConsumerInterceptor;

    private Object[] arguments;

    private Class[] argumentType;

    private MessageDispatch messageDispatch;

    public class Des extends ActiveMQDestination {

        @Override
        protected String getQualifiedPrefix() {
            return null;
        }

        @Override
        public byte getDestinationType() {
            return 1;
        }

        @Override
        public byte getDataStructureType() {
            return 0;
        }
    }

    public class Msg extends Message {

        @Override
        public Message copy() {
            return null;
        }

        @Override
        public void clearBody() throws JMSException {

        }

        @Override
        public void storeContent() {

        }

        @Override
        public void storeContentAndClear() {

        }

        @Override
        public Response visit(CommandVisitor commandVisitor) throws Exception {
            return null;
        }

        @Override
        public byte getDataStructureType() {
            return 0;
        }
    }

    private EnhancedInstance enhancedInstance = new EnhancedInstance() {
        @Override
        public Object getSkyWalkingDynamicField() {
            return "localhost:60601";
        }

        @Override
        public void setSkyWalkingDynamicField(Object value) {
        }
    };

    @Before
    public void setUp() throws IOException {
        activeMQConsumerInterceptor = new ActiveMQConsumerInterceptor();
        messageDispatch = new MessageDispatch();

        Des des = new Des();
        des.setPhysicalName("test");
        messageDispatch.setDestination(des);
        Message msg = new Msg();
        messageDispatch.setMessage(msg);
        arguments = new Object[] {messageDispatch};
        argumentType = null;
    }

    @Test
    public void testConsumerWithoutMessage() throws Throwable {
        activeMQConsumerInterceptor.beforeMethod(enhancedInstance, null, arguments, null, null);
        activeMQConsumerInterceptor.afterMethod(enhancedInstance, null, arguments, null, null);

        List<TraceSegment> traceSegments = segmentStorage.getTraceSegments();
        Assert.assertThat(traceSegments.size(), is(1));
    }

}