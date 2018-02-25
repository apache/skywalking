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

public class HystrixCommandConstructorInterceptor implements InstanceConstructorInterceptor {

    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        String identifyKey = "";

        if (HystrixCommand.class.isAssignableFrom(objInst.getClass())) {
            HystrixCommand hystrixCommand = (HystrixCommand)objInst;
            identifyKey = hystrixCommand.getCommandKey().name();
        }

        if (HystrixCollapser.class.isAssignableFrom(objInst.getClass())) {
            HystrixCollapser hystrixCommand = (HystrixCollapser)objInst;
            identifyKey = hystrixCommand.getCollapserKey().name();
        }

        if (HystrixObservableCollapser.class.isAssignableFrom(objInst.getClass())) {
            HystrixObservableCollapser hystrixCommand = (HystrixObservableCollapser)objInst;
            identifyKey = hystrixCommand.getCollapserKey().name();
        }

        if (HystrixObservableCommand.class.isAssignableFrom(objInst.getClass())) {
            HystrixObservableCommand hystrixCommand = (HystrixObservableCommand)objInst;
            identifyKey = hystrixCommand.getCommandKey().name();
        }

        objInst.setSkyWalkingDynamicField(new EnhanceRequireObjectCache("Hystrix/" + identifyKey));
    }

}
