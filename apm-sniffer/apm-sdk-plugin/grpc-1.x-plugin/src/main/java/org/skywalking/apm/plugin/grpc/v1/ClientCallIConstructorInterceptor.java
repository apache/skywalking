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

package org.skywalking.apm.plugin.grpc.v1;

import io.grpc.MethodDescriptor;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.skywalking.apm.plugin.grpc.v1.vo.GRPCDynamicFields;

/**
 * {@link ClientCallIConstructorInterceptor} pass the {@link GRPCDynamicFields} into the
 * <code>io.grpc.internal.ClientCallImpl</code> instance for propagate the information of build span.
 *
 * @author zhangxin
 */
public class ClientCallIConstructorInterceptor implements InstanceConstructorInterceptor {

    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        GRPCDynamicFields dynamicFields = new GRPCDynamicFields();
        dynamicFields.setDescriptor((MethodDescriptor)allArguments[0]);
        objInst.setSkyWalkingDynamicField(dynamicFields);
    }
}
