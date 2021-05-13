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

package org.apache.skywalking.apm.plugin.play.v2x;

import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.junit.Assert;
import org.junit.Test;
import play.api.inject.BindingKey;
import play.api.inject.Injector;
import scala.collection.immutable.Seq;
import scala.collection.immutable.Seq$;
import scala.reflect.ClassTag;

import java.util.Objects;

public class HttpFiltersInterceptorTest {

    private EnhancedInstance enhancedInstance = new EnhancedInstance() {
        private Object object = null;

        @Override
        public Object getSkyWalkingDynamicField() {
            return object;
        }

        @Override
        public void setSkyWalkingDynamicField(Object value) {
            this.object = value;
        }
    };

    private HttpFiltersInterceptor interceptor = new HttpFiltersInterceptor();
    private Injector injector = new Injector() {
        @Override
        public <T> T instanceOf(ClassTag<T> evidence) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T instanceOf(Class<T> clazz) {
            return (T) new TracingFilter(null);
        }

        @Override
        public <T> T instanceOf(BindingKey<T> key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public play.inject.Injector asJava() {
            throw new UnsupportedOperationException();
        }
    };

    @Test
    public void testBindingInjector() {
        Object[] arguments = new Object[] {
            null,
            null,
            injector
        };
        interceptor.onConstruct(enhancedInstance, arguments);
        Assert.assertTrue(Objects.nonNull(enhancedInstance.getSkyWalkingDynamicField()));
        Assert.assertTrue(enhancedInstance.getSkyWalkingDynamicField() instanceof Injector);
    }

    @Test
    public void testReturningTracingFilter() throws Throwable {
        Seq ret = Seq$.MODULE$.empty();
        enhancedInstance.setSkyWalkingDynamicField(injector);
        Object result = interceptor.afterMethod(enhancedInstance, null, null, null, ret);
        Assert.assertTrue(Objects.nonNull(result));
        Seq filters = (Seq) result;
        Assert.assertTrue(filters.head() instanceof TracingFilter);
    }

}
