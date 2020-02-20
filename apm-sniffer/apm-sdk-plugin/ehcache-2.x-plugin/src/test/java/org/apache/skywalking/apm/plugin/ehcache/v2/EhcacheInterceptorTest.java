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

package org.apache.skywalking.apm.plugin.ehcache.v2;

import java.lang.reflect.Method;
import java.util.List;
import net.sf.ehcache.Cache;
import net.sf.ehcache.Element;
import net.sf.ehcache.config.CacheConfiguration;
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

import static org.apache.skywalking.apm.plugin.ehcache.v2.define.EhcachePluginInstrumentation.GET_ALL_CACHE_ENHANCE_METHOD;
import static org.apache.skywalking.apm.plugin.ehcache.v2.define.EhcachePluginInstrumentation.GET_CACHE_ENHANCE_METHOD;
import static org.apache.skywalking.apm.plugin.ehcache.v2.define.EhcachePluginInstrumentation.PUT_CACHE_ENHANCE_METHOD;
import static org.apache.skywalking.apm.plugin.ehcache.v2.define.EhcachePluginInstrumentation.READ_LOCK_RELEASE_ENHANCE_METHOD;
import static org.apache.skywalking.apm.plugin.ehcache.v2.define.EhcachePluginInstrumentation.READ_LOCK_TRY_ENHANCE_METHOD;
import static org.apache.skywalking.apm.plugin.ehcache.v2.define.EhcachePluginInstrumentation.WRITE_LOCK_RELEASE_ENHANCE_METHOD;
import static org.apache.skywalking.apm.plugin.ehcache.v2.define.EhcachePluginInstrumentation.WRITE_LOCK_TRY_ENHANCE_METHOD;
import static org.apache.skywalking.apm.plugin.ehcache.v2.define.EhcachePluginInstrumentation.CACHE_NAME_ENHANCE_METHOD;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;

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
    private EhcacheOperateAllInterceptor operateAllInterceptor;
    private EhcacheLockInterceptor lockInterceptor;
    private EhcacheConstructorInterceptor constructorInterceptor;
    private EhcachePrivateConstructorInterceptor privateConstructorInterceptor;
    private EhcacheCacheNameInterceptor cacheNameInterceptor;
    private Object[] operateObjectArguments;
    private Object[] operateElementArguments;
    private Object[] tryLockArguments;
    private Object[] releaseLockArguments;
    private Object[] cacheNameArguments;

    private Exception exception;

    private Method putCacheMethod;
    private Method getCacheMethod;
    private Method getAllMethod;

    private Method tryReadLockMethod;
    private Method tryWriteLockMethod;
    private Method releaseReadLockMethod;
    private Method releaseWriteLockMethod;

    private Method setNameMethod;

    private EnhancedInstance enhancedInstance = new EnhancedInstance() {
        EhcacheEnhanceInfo ehcacheEnhanceInfo;

        @Override
        public Object getSkyWalkingDynamicField() {
            return ehcacheEnhanceInfo;
        }

        @Override
        public void setSkyWalkingDynamicField(Object value) {
            ehcacheEnhanceInfo = (EhcacheEnhanceInfo) value;
        }
    };

    @Before
    public void setUp() throws NoSuchMethodException {
        operateObjectInterceptor = new EhcacheOperateObjectInterceptor();
        operateElementInterceptor = new EhcacheOperateElementInterceptor();
        operateAllInterceptor = new EhcacheOperateAllInterceptor();
        constructorInterceptor = new EhcacheConstructorInterceptor();
        privateConstructorInterceptor = new EhcachePrivateConstructorInterceptor();
        cacheNameInterceptor = new EhcacheCacheNameInterceptor();
        lockInterceptor = new EhcacheLockInterceptor();

        exception = new Exception();

        operateObjectArguments = new Object[] {"dataKey"};
        operateElementArguments = new Element[] {new Element("dataKey", 1)};
        tryLockArguments = new Object[] {
            "dataKey",
            3000
        };
        releaseLockArguments = new Object[] {"dataKey"};
        cacheNameArguments = new Object[] {"cacheName"};

        putCacheMethod = Whitebox.getMethods(Cache.class, PUT_CACHE_ENHANCE_METHOD)[0];
        getCacheMethod = Whitebox.getMethods(Cache.class, GET_CACHE_ENHANCE_METHOD)[0];
        getAllMethod = Whitebox.getMethods(Cache.class, GET_ALL_CACHE_ENHANCE_METHOD)[0];

        tryReadLockMethod = Whitebox.getMethods(Cache.class, READ_LOCK_TRY_ENHANCE_METHOD)[0];
        tryWriteLockMethod = Whitebox.getMethods(Cache.class, WRITE_LOCK_TRY_ENHANCE_METHOD)[0];
        releaseReadLockMethod = Whitebox.getMethods(Cache.class, READ_LOCK_RELEASE_ENHANCE_METHOD)[0];
        releaseWriteLockMethod = Whitebox.getMethods(Cache.class, WRITE_LOCK_RELEASE_ENHANCE_METHOD)[0];

        setNameMethod = Whitebox.getMethods(Cache.class, CACHE_NAME_ENHANCE_METHOD)[0];

        enhancedInstance.setSkyWalkingDynamicField(new EhcacheEnhanceInfo(CACHE_NAME));
    }

    @Test
    public void assertConstruct() throws Throwable {
        constructorInterceptor.onConstruct(enhancedInstance, new Object[] {new CacheConfiguration(CACHE_NAME, 20)});
    }

    @Test
    public void assertPrivateConstruct() throws Throwable {
        privateConstructorInterceptor.onConstruct(enhancedInstance, new Object[] {new Cache(new CacheConfiguration(CACHE_NAME, 20))});
    }

    @Test
    public void assertSetNameSuccess() throws Throwable {
        cacheNameInterceptor.beforeMethod(enhancedInstance, setNameMethod, cacheNameArguments, null, null);
        cacheNameInterceptor.handleMethodException(enhancedInstance, setNameMethod, null, null, exception);
        cacheNameInterceptor.afterMethod(enhancedInstance, setNameMethod, cacheNameArguments, null, null);

        Assert.assertThat(((EhcacheEnhanceInfo) enhancedInstance.getSkyWalkingDynamicField()).getCacheName(), equalTo("cacheName"));
    }

    @Test
    public void assertPutSuccess() throws Throwable {
        // put arguments
        operateElementInterceptor.beforeMethod(enhancedInstance, putCacheMethod, operateElementArguments, null, null);
        operateElementInterceptor.handleMethodException(enhancedInstance, putCacheMethod, null, null, exception);
        operateElementInterceptor.afterMethod(enhancedInstance, putCacheMethod, operateElementArguments, null, null);

        List<TraceSegment> traceSegments = segmentStorage.getTraceSegments();
        Assert.assertThat(traceSegments.size(), is(1));
    }

    @Test
    public void assertGetSuccess() throws Throwable {
        operateObjectInterceptor.beforeMethod(enhancedInstance, getCacheMethod, operateObjectArguments, null, null);
        operateObjectInterceptor.handleMethodException(enhancedInstance, getCacheMethod, null, null, exception);
        operateObjectInterceptor.afterMethod(enhancedInstance, getCacheMethod, operateObjectArguments, null, null);

        List<TraceSegment> traceSegments = segmentStorage.getTraceSegments();
        Assert.assertThat(traceSegments.size(), is(1));
    }

    @Test
    public void assertGetAllSuccess() throws Throwable {
        operateAllInterceptor.beforeMethod(enhancedInstance, getAllMethod, null, null, null);
        operateAllInterceptor.handleMethodException(enhancedInstance, getAllMethod, null, null, exception);
        operateAllInterceptor.afterMethod(enhancedInstance, getAllMethod, null, null, null);

        List<TraceSegment> traceSegments = segmentStorage.getTraceSegments();
        Assert.assertThat(traceSegments.size(), is(1));
    }

    @Test
    public void assertLockSuccess() throws Throwable {
        lockInterceptor.beforeMethod(enhancedInstance, tryReadLockMethod, tryLockArguments, null, null);
        lockInterceptor.handleMethodException(enhancedInstance, tryReadLockMethod, null, null, exception);
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
