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

package org.apache.skywalking.apm.plugin.thrift;

import java.util.Map;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.thrift.ProcessFunction;
import org.apache.thrift.TBase;
import org.apache.thrift.TBaseProcessor;

/**
 * @see TBaseProcessor
 */
public class TBaseProcessorInterceptor extends AbstractProcessorInterceptor {
    private Map<String, ProcessFunction> functionMapView;

    @Override
    public void onConstruct(final EnhancedInstance enhancedInstance, final Object[] objects) {
        enhancedInstance.setSkyWalkingDynamicField(objects[0].getClass().getName() + ".");
        functionMapView = ((TBaseProcessor) objects[1]).getProcessMapView();
    }

    @Override
    protected TBase<?, ?> getFunction(final String method) {
        return functionMapView.get(method).getEmptyArgsInstance();
    }
}
