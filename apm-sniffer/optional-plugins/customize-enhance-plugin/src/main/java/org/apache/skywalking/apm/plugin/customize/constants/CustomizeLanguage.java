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

package org.apache.skywalking.apm.plugin.customize.constants;

import org.apache.skywalking.apm.plugin.customize.interceptor.defalut.DefaultInstanceInterceptor;
import org.apache.skywalking.apm.plugin.customize.interceptor.defalut.DefaultStaticInterceptor;
import org.apache.skywalking.apm.plugin.customize.interceptor.spel.SpELInstanceInterceptor;
import org.apache.skywalking.apm.plugin.customize.interceptor.spel.SpELStaticInterceptor;

/**
 * Custom enhanced support syntax, DEFAULT and SpEL.
 *
 * @author zhaoyuguang
 */

public enum CustomizeLanguage {

    DEFAULT(DefaultInstanceInterceptor.class, DefaultStaticInterceptor.class),
    SpEL(SpELInstanceInterceptor.class, SpELStaticInterceptor.class);

    private Class instanceInterceptor;
    private Class staticInterceptor;

    CustomizeLanguage(Class instanceInterceptor, Class staticInterceptor) {
        this.instanceInterceptor = instanceInterceptor;
        this.staticInterceptor = staticInterceptor;
    }

    public Class getInterceptor(boolean isStatic) {
        return isStatic ?  staticInterceptor : instanceInterceptor;
    }
}
