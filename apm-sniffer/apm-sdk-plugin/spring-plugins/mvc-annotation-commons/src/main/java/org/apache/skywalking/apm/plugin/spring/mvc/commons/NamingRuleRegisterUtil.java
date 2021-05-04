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

package org.apache.skywalking.apm.plugin.spring.mvc.commons;

import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.naming.EndpointNamingControl;
import org.apache.skywalking.apm.agent.core.naming.NamingRule;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

public class NamingRuleRegisterUtil {
    private static final ILog LOGGER = LogManager.getLogger(NamingRuleRegisterUtil.class);

    public static void prepare4Register(EnhancedInstance objInst) {
        if (objInst == null) {
            return;
        }
        EndpointNamingControl endpointNamingControl = ServiceManager.INSTANCE.findService(EndpointNamingControl.class);
        if (endpointNamingControl == null) {
            return;
        }
        NamingRule namingRule = new NamingRule(null, ComponentsDefine.SPRING_MVC_ANNOTATION);
        namingRule.setDetails(objInst);
        endpointNamingControl.addNamingRule(namingRule);
    }
}
