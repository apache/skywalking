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


package com.apache.skywalking.aom.plugin.ehcache.v2;

import com.apache.skywalking.apm.plugin.ehcache.v2.EhcacheEnhanceInfo;
import com.apache.skywalking.apm.plugin.ehcache.v2.EhcacheLockInterceptor;
import com.apache.skywalking.apm.plugin.ehcache.v2.EhcacheOperateElementInterceptor;
import com.apache.skywalking.apm.plugin.ehcache.v2.EhcacheOperateObjectInterceptor;
import net.sf.ehcache.Cache;
import net.sf.ehcache.Element;
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
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
import org.powermock.reflect.Whitebox;

import java.lang.reflect.Method;
import java.util.List;

import static com.apache.skywalking.apm.plugin.ehcache.v2.define.EhcachePluginInstrumentation.*;
import static org.hamcrest.CoreMatchers.is;

/**
 * @author MrPro
 */
@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class EhcacheInterceptorTest {

    private static final String CACHE_NAME = "test";

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    private EhcacheOperateObjectInterceptor operateObjectInterceptor;
    private EhcacheOperateElementInterceptor operateElementInterceptor;
    private EhcacheLockInterceptor lockInterceptor;
    private Object[] operateObjectArguments;
    private Object[] operateElementArguments;
    private Object[] tryLockArguments;
    private Object[] releaseLockArguments;

    private Method putCacheMethod;
    private Method getCacheMethod;

    private Method tryReadLockMethod;
    private Method tryWriteLockMethod;
    private Method releaseReadLockMethod;
    private Method releaseWriteLockMethod;

    private EnhancedInstance enhancedInstance = new EnhancedInstance() {
        @Override
        public Object getSkyWalkingDynamicField() {
            return new EhcacheEnhanceInfo(CACHE_NAME);
        }

        @Override
        public void setSkyWalkingDynamicField(Object value) {
        }
    };

    @Before
    public void setUp() throws NoSuchMethodException {
        operateObjectInterceptor = new EhcacheOperateObjectInterceptor();
        operateElementInterceptor = new EhcacheOperateElementInterceptor();
        lockInterceptor = new EhcacheLockInterceptor();

        operateObjectArguments = new Object[] {
                "dataKey"
        };
        operateElementArguments = new Element[] {
                new Element("dataKey", 1)
        };
        tryLockArguments = new Object[] {
                "dataKey", 3000
        };
        releaseLockArguments = new Object[] {
                "dataKey"
        };

        putCacheMethod = Whitebox.getMethods(Cache.class, PUT_CACHE_ENHANCE_METHOD)[0];
        getCacheMethod = Whitebox.getMethods(Cache.class, GET_CACHE_ENHANCE_METHOD)[0];

        tryReadLockMethod = Whitebox.getMethods(Cache.class, READ_LOCK_TRY_ENHANCE_METHOD)[0];
        tryWriteLockMethod = Whitebox.getMethods(Cache.class, WRITE_LOCK_TRY_ENHANCE_METHOD)[0];
        releaseReadLockMethod = Whitebox.getMethods(Cache.class, READ_LOCK_RELEASE_ENHANCE_METHOD)[0];
        releaseWriteLockMethod = Whitebox.getMethods(Cache.class, WRITE_LOCK_RELEASE_ENHANCE_METHOD)[0];
    }

    @Test
    public void assertPutSuccess() throws Throwable {
        // put arguments
        operateElementInterceptor.beforeMethod(enhancedInstance, putCacheMethod, operateElementArguments, null, null);
        operateElementInterceptor.afterMethod(enhancedInstance, putCacheMethod, operateElementArguments, null, null);

        List<TraceSegment> traceSegments = segmentStorage.getTraceSegments();
        Assert.assertThat(traceSegments.size(), is(1));
    }

    @Test
    public void assertGetSuccess() throws Throwable {
        operateObjectInterceptor.beforeMethod(enhancedInstance, getCacheMethod, operateObjectArguments, null, null);
        operateObjectInterceptor.afterMethod(enhancedInstance, getCacheMethod, operateObjectArguments, null, null);

        List<TraceSegment> traceSegments = segmentStorage.getTraceSegments();
        Assert.assertThat(traceSegments.size(), is(1));
    }

    @Test
    public void assertLockSuccess() throws Throwable {
        lockInterceptor.beforeMethod(enhancedInstance, tryReadLockMethod, tryLockArguments, null, null);
        lockInterceptor.afterMethod(enhancedInstance, tryReadLockMethod, tryLockArguments, null, null);

        lockInterceptor.beforeMethod(enhancedInstance, tryWriteLockMethod, tryLockArguments, null, null);
        lockInterceptor.afterMethod(enhancedInstance, tryWriteLockMethod, tryLockArguments, null, null);

        lockInterceptor.beforeMethod(enhancedInstance, releaseReadLockMethod, releaseLockArguments, null, null);
        lockInterceptor.afterMethod(enhancedInstance, releaseReadLockMethod, releaseLockArguments, null, null);

        lockInterceptor.beforeMethod(enhancedInstance, releaseWriteLockMethod, releaseLockArguments, null, null);
        lockInterceptor.afterMethod(enhancedInstance, releaseWriteLockMethod, releaseLockArguments, null, null);

        List<TraceSegment> traceSegments = segmentStorage.getTraceSegments();
        Assert.assertThat(traceSegments.size(), is(4));
    }
}
