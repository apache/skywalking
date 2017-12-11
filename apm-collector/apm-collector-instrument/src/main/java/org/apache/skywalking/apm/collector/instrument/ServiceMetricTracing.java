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


package org.apache.skywalking.apm.collector.instrument;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.implementation.bind.annotation.This;

/**
 * @author wu-sheng
 */
public class ServiceMetricTracing {
    private MetricCollector.ServiceMetric serviceMetric;

    public ServiceMetricTracing(String module, String provider, String service) {
        serviceMetric = MetricCollector.INSTANCE.registerService(module, provider, service);
    }

    @RuntimeType
    public Object intercept(@This Object obj,
        @AllArguments Object[] allArguments,
        @SuperCall Callable<?> zuper,
        @Origin Method method
    ) throws Throwable {
        boolean occurError = false;
        long startNano = System.nanoTime();
        long endNano;
        try {
            return zuper.call();
        } catch (Throwable t) {
            occurError = true;
            throw t;
        } finally {
            endNano = System.nanoTime();
            serviceMetric.trace(method, endNano - startNano, occurError);
        }
    }
}
