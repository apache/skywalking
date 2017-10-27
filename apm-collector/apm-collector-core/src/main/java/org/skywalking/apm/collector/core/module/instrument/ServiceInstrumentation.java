/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.core.module.instrument;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatcher;
import org.skywalking.apm.collector.core.module.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

/**
 * @author wu-sheng
 */
public enum ServiceInstrumentation {
    INSTANCE;

    private final Logger logger = LoggerFactory.getLogger(ServiceInstrumentation.class);
    private ElementMatcher<? super MethodDescription> excludeObjectMethodsMatcher;

    public Service buildServiceUnderMonitor(Service implementation) {
        if (TracedService.class.isInstance(implementation)) {
            // Duplicate service instrument, ignore.
            return implementation;
        }
        try {
            return new ByteBuddy().subclass(implementation.getClass())
                .implement(TracedService.class)
                .method(getDefaultMatcher()).intercept(
                    MethodDelegation.withDefaultConfiguration().to(new ServiceMetricCollector())
                ).make().load(getClass().getClassLoader()
                ).getLoaded().newInstance();
        } catch (InstantiationException e) {
            logger.error("Create instrumented service " + implementation.getClass() + " fail.", e);
        } catch (IllegalAccessException e) {
            logger.error("Create instrumented service " + implementation.getClass() + " fail.", e);
        }
        return implementation;
    }

    private ElementMatcher<? super MethodDescription> getDefaultMatcher() {
        if (excludeObjectMethodsMatcher == null) {
            excludeObjectMethodsMatcher = not(isStatic().or(named("getClass")).or(named("hashCode")).or(named("equals")).or(named("clone"))
                .or(named("toString")).or(named("notify")).or(named("notifyAll")).or(named("wait")).or(named("finalize")));
        }
        return excludeObjectMethodsMatcher;
    }
}
