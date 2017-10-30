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

package org.skywalking.apm.plugin.jdbc.h2;

import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;

/**
 * {@link PooledJdbcConnectionConstructorInterceptor } store {@link org.skywalking.apm.plugin.jdbc.trace.ConnectionInfo}
 * when the client new instance of {@link org.h2.jdbcx.JdbcXAConnection$PooledJdbcConnection}.
 *
 * @author zhangxin
 */
public class PooledJdbcConnectionConstructorInterceptor implements InstanceConstructorInterceptor {
    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        objInst.setSkyWalkingDynamicField(((EnhancedInstance)allArguments[0]).getSkyWalkingDynamicField());
    }
}
