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

package org.apache.skywalking.apm.plugin.solr.shard;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.plugin.solr.commons.NamesMap;
import org.apache.skywalking.apm.plugin.solr.wrappers.ModSolrParamsWrapper;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.ShardParams;
import org.apache.solr.common.params.SolrParams;

import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;

import static org.apache.skywalking.apm.plugin.solr.commons.Constants.OPER_SHARD_PREF;
import static org.apache.skywalking.apm.plugin.solr.commons.Constants.SKIP_ADMIN_PREF;
import static org.apache.skywalking.apm.plugin.solr.commons.Constants.SW_ENHANCE_FLAG;

public class ShardRequestInterceptor implements InstanceMethodsAroundInterceptor, InstanceConstructorInterceptor {

    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        HttpSolrClient client = (HttpSolrClient) objInst;
        String peer = "", core = "";
        try {
            URL url = new URL(client.getBaseURL());
            peer = url.getHost() + ":" + url.getPort();
            int idx = url.getPath().lastIndexOf('/');
            if (idx > 0) {
                core = url.getPath().substring(idx + 1);
            }
        } catch (MalformedURLException ignore) {
        }
        objInst.setSkyWalkingDynamicField(new String[]{peer, core});
    }

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        SolrRequest<?> request = (SolrRequest<?>) allArguments[0];
        if (request.getPath().startsWith(SKIP_ADMIN_PREF))
            return;

        String[] info = (String[]) objInst.getSkyWalkingDynamicField();
        SolrParams params = request.getParams();

        ContextSnapshot snapshot = null;
        String operationName = OPER_SHARD_PREF + info[1] + request.getPath();
        if (params.getBool(SW_ENHANCE_FLAG, false)) {
            if (params instanceof ModSolrParamsWrapper) {
                EnhancedInstance instance = (EnhancedInstance) request.getParams();
                snapshot = (ContextSnapshot) instance.getSkyWalkingDynamicField();
            }
            ((ModifiableSolrParams) params).remove(SW_ENHANCE_FLAG);
            final int purpose = params.getInt(ShardParams.SHARDS_PURPOSE, 0);
            operationName = OPER_SHARD_PREF + NamesMap.gePurposeName(purpose);
        }

        AbstractSpan span = ContextManager.createExitSpan(operationName, info[0]);
        span.setComponent(ComponentsDefine.SOLR);
        span.setLayer(SpanLayer.HTTP);

        if (snapshot != null) {
            ContextManager.continued(snapshot);
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret) throws Throwable {
        SolrRequest<?> request = (SolrRequest<?>) allArguments[0];
        if (!request.getPath().startsWith(SKIP_ADMIN_PREF) && ContextManager.isActive()) {
            ContextManager.stopSpan();
        }
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Throwable t) {
        if (ContextManager.isActive()) {
            ContextManager.activeSpan().errorOccurred().log(t);
        }
    }

}
