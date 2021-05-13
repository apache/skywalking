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

import java.util.List;

import org.apache.activemq.ActiveMQMessageProducer;
import org.apache.activemq.command.ActiveMQDestination;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.test.tools.SpanAssert;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import java.util.Enumeration;

import static org.apache.skywalking.apm.network.trace.component.ComponentsDefine.ACTIVEMQ_PRODUCER;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class ActiveMQProducerInterceptorTest {

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    private ActiveMQProducerInterceptor producerInterceptor;

    private Object[] arguments;

    private Class[] argumentType;

    private MQDestination mqDestination;

    private Message message;

    private class MQDestination extends ActiveMQDestination {

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
            return 1;
        }
    }

    public class Msg implements Message {

        @Override
        public String getJMSMessageID() throws JMSException {
            return null;
        }

        @Override
        public void setJMSMessageID(String s) throws JMSException {

        }

        @Override
        public long getJMSTimestamp() throws JMSException {
            return 0;
        }

        @Override
        public void setJMSTimestamp(long l) throws JMSException {

        }

        @Override
        public byte[] getJMSCorrelationIDAsBytes() throws JMSException {
            return new byte[0];
        }

        @Override
        public void setJMSCorrelationIDAsBytes(byte[] bytes) throws JMSException {

        }

        @Override
        public void setJMSCorrelationID(String s) throws JMSException {

        }

        @Override
        public String getJMSCorrelationID() throws JMSException {
            return null;
        }

        @Override
        public Destination getJMSReplyTo() throws JMSException {
            return null;
        }

        @Override
        public void setJMSReplyTo(Destination destination) throws JMSException {

        }

        @Override
        public Destination getJMSDestination() throws JMSException {
            return null;
        }

        @Override
        public void setJMSDestination(Destination destination) throws JMSException {

        }

        @Override
        public int getJMSDeliveryMode() throws JMSException {
            return 0;
        }

        @Override
        public void setJMSDeliveryMode(int i) throws JMSException {

        }

        @Override
        public boolean getJMSRedelivered() throws JMSException {
            return false;
        }

        @Override
        public void setJMSRedelivered(boolean b) throws JMSException {

        }

        @Override
        public String getJMSType() throws JMSException {
            return null;
        }

        @Override
        public void setJMSType(String s) throws JMSException {

        }

        @Override
        public long getJMSExpiration() throws JMSException {
            return 0;
        }

        @Override
        public void setJMSExpiration(long l) throws JMSException {

        }

        @Override
        public int getJMSPriority() throws JMSException {
            return 0;
        }

        @Override
        public void setJMSPriority(int i) throws JMSException {

        }

        @Override
        public void clearProperties() throws JMSException {

        }

        @Override
        public boolean propertyExists(String s) throws JMSException {
            return false;
        }

        @Override
        public boolean getBooleanProperty(String s) throws JMSException {
            return false;
        }

        @Override
        public byte getByteProperty(String s) throws JMSException {
            return 0;
        }

        @Override
        public short getShortProperty(String s) throws JMSException {
            return 0;
        }

        @Override
        public int getIntProperty(String s) throws JMSException {
            return 0;
        }

        @Override
        public long getLongProperty(String s) throws JMSException {
            return 0;
        }

        @Override
        public float getFloatProperty(String s) throws JMSException {
            return 0;
        }

        @Override
        public double getDoubleProperty(String s) throws JMSException {
            return 0;
        }

        @Override
        public String getStringProperty(String s) throws JMSException {
            return null;
        }

        @Override
        public Object getObjectProperty(String s) throws JMSException {
            return null;
        }

        @Override
        public Enumeration getPropertyNames() throws JMSException {
            return null;
        }

        @Override
        public void setBooleanProperty(String s, boolean b) throws JMSException {

        }

        @Override
        public void setByteProperty(String s, byte b) throws JMSException {

        }

        @Override
        public void setShortProperty(String s, short i) throws JMSException {

        }

        @Override
        public void setIntProperty(String s, int i) throws JMSException {

        }

        @Override
        public void setLongProperty(String s, long l) throws JMSException {

        }

        @Override
        public void setFloatProperty(String s, float v) throws JMSException {

        }

        @Override
        public void setDoubleProperty(String s, double v) throws JMSException {

        }

        @Override
        public void setStringProperty(String s, String s1) throws JMSException {

        }

        @Override
        public void setObjectProperty(String s, Object o) throws JMSException {

        }

        @Override
        public void acknowledge() throws JMSException {

        }

        @Override
        public void clearBody() throws JMSException {

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
    public void setUp() {
        producerInterceptor = new ActiveMQProducerInterceptor();
        mqDestination = new MQDestination();
        mqDestination.setPhysicalName("test");
        message = new Msg();
        arguments = new Object[] {
            mqDestination,
            message
        };
        argumentType = new Class[] {ActiveMQMessageProducer.class};

    }

    @Test
    public void testSendMessage() throws Throwable {
        producerInterceptor.beforeMethod(enhancedInstance, null, arguments, null, null);
        producerInterceptor.afterMethod(enhancedInstance, null, arguments, null, null);

        List<TraceSegment> traceSegmentList = segmentStorage.getTraceSegments();
        assertThat(traceSegmentList.size(), is(1));

        TraceSegment segment = traceSegmentList.get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(segment);
        assertThat(spans.size(), is(1));
        assertMessageSpan(spans.get(0));
    }

    private void assertMessageSpan(AbstractTracingSpan span) {
        SpanAssert.assertTag(span, 0, "localhost:60601");
        SpanAssert.assertTag(span, 1, "test");
        SpanAssert.assertComponent(span, ACTIVEMQ_PRODUCER);
        SpanAssert.assertLayer(span, SpanLayer.MQ);
        assertThat(span.getOperationName(), is("ActiveMQ/Queue/test/Producer"));
    }
}
