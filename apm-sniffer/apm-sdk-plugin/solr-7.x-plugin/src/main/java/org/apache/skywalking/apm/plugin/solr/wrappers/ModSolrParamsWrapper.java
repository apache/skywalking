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

package org.apache.skywalking.apm.plugin.solr.wrappers;

import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.plugin.solr.commons.Constants;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;

public class ModSolrParamsWrapper extends ModifiableSolrParams implements EnhancedInstance {
    private static final long serialVersionUID = 9015331918376176370L;
    private Object dynamic = null;

    public ModSolrParamsWrapper(SolrParams params) {
        super(params);
        set(Constants.SW_ENHANCE_FLAG, true);
    }

    @Override
    public Object getSkyWalkingDynamicField() {
        return dynamic;
    }

    @Override
    public void setSkyWalkingDynamicField(Object object) {
        this.dynamic = object;
    }

}
