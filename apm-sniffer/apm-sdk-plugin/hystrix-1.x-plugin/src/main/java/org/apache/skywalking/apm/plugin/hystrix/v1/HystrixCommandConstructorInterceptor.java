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

package org.apache.skywalking.apm.plugin.hystrix.v1;

import com.netflix.hystrix.HystrixCollapser;
import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixObservableCollapser;
import com.netflix.hystrix.HystrixObservableCommand;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;

/**
 * {@link HystrixCommandConstructorInterceptor} get <code>CommandKey</code> or <code>CollapserKey</code> as the
 * operation name prefix of span when the constructor that the class hierarchy <code>com.netflix.hystrix.HystrixCommand</code>
 * invoked.
 */
public class HystrixCommandConstructorInterceptor implements InstanceConstructorInterceptor {

    public static final String OPERATION_NAME_PREFIX = "Hystrix/";

    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        String commandIdentify = "";

        if (HystrixCommand.class.isAssignableFrom(objInst.getClass())) {
            HystrixCommand hystrixCommand = (HystrixCommand) objInst;
            commandIdentify = hystrixCommand.getCommandKey().name();
        } else if (HystrixCollapser.class.isAssignableFrom(objInst.getClass())) {
            HystrixCollapser hystrixCollapser = (HystrixCollapser) objInst;
            commandIdentify = hystrixCollapser.getCollapserKey().name();
        } else if (HystrixObservableCollapser.class.isAssignableFrom(objInst.getClass())) {
            HystrixObservableCollapser hystrixObservableCollapser = (HystrixObservableCollapser) objInst;
            commandIdentify = hystrixObservableCollapser.getCollapserKey().name();
        } else if (HystrixObservableCommand.class.isAssignableFrom(objInst.getClass())) {
            HystrixObservableCommand hystrixObservableCommand = (HystrixObservableCommand) objInst;
            commandIdentify = hystrixObservableCommand.getCommandKey().name();
        }

        objInst.setSkyWalkingDynamicField(new EnhanceRequireObjectCache(OPERATION_NAME_PREFIX + commandIdentify));
    }

}
