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

package org.skywalking.apm.plugin.jdbc.h2.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.skywalking.apm.agent.core.plugin.match.ClassMatch;

import static net.bytebuddy.matcher.ElementMatchers.any;
import static org.skywalking.apm.agent.core.plugin.match.NameMatch.byName;

/**
 * {@link PooledJdbcConnectionInstrumentation} presents that skywalking intercepts {@link
 * org.h2.jdbcx.JdbcXAConnection}.
 *
 * @author zhangxin
 */
public class PooledJdbcConnectionInstrumentation extends AbstractConnectionInstrumentation {

    public static final String CONSTRUCTOR_INTERCEPT_CLASS = "org.skywalking.apm.plugin.jdbc.h2.PooledJdbcConnectionConstructorInterceptor";
    public static final String ENHANCE_CLASS = "org.h2.jdbcx.JdbcXAConnection$PooledJdbcConnection";

    @Override protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[] {
            new ConstructorInterceptPoint() {
                @Override public ElementMatcher<MethodDescription> getConstructorMatcher() {
                    return any();
                }

                @Override public String getConstructorInterceptor() {
                    return CONSTRUCTOR_INTERCEPT_CLASS;
                }
            }
        };
    }

    @Override protected ClassMatch enhanceClass() {
        return byName(ENHANCE_CLASS);
    }
}
