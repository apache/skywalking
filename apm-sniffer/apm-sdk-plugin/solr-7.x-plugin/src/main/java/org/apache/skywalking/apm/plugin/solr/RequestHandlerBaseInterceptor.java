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

package org.apache.skywalking.apm.plugin.solr;

import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.solr.common.params.ShardParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.update.processor.DistributingUpdateProcessorFactory;
import org.slf4j.MDC;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;

import static org.apache.skywalking.apm.plugin.solr.commons.Constants.*;

public class RequestHandlerBaseInterceptor implements InstanceMethodsAroundInterceptor {
    private static final ILog LOG = LogManager.getLogger(RequestHandlerBaseInterceptor.class);

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        final SolrQueryRequest request = (SolrQueryRequest) allArguments[0];
        if (Config.Plugin.Solr.SKIP_ADMIN_REQUEST && request.getPath().startsWith(SKIP_ADMIN_PREF)) {
            return;
        }

        HttpServletRequest req = request.getHttpSolrCall().getReq();
        ContextCarrier carrier = new ContextCarrier();
        CarrierItem items = carrier.items();
        while (items.hasNext()) {
            items = items.next();
            items.setHeadValue(req.getHeader(items.getHeadKey()));
        }

        SolrParams params = request.getParams();
        String operationPref = OPER_SOLR_PREF;

        if ((params.getBool(ShardParams.IS_SHARD, false) && params.getInt(ShardParams.SHARDS_PURPOSE, 0) != 0)
                || params.get(DistributingUpdateProcessorFactory.DISTRIB_UPDATE_PARAM) != null) {
            operationPref = OPER_SHARD_PREF;
        }

        createSpan(operationPref + req.getServletPath(), carrier);
        if (Config.Plugin.Solr.PRINT_TRACE_ID_ON_LOG) {
            MDC.put(MDC_KEY_TRACE_ID, TRACE_ID_PREF + ContextManager.getGlobalTraceId());
        }
    }

    private final AbstractSpan createSpan(String operation, ContextCarrier carrier) {
        AbstractSpan span = ContextManager.createEntrySpan(operation, carrier);
        span.setComponent(ComponentsDefine.SOLR)
                .setLayer(SpanLayer.HTTP);
        return span;
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret) throws Throwable {
        final SolrQueryRequest request = (SolrQueryRequest) allArguments[0];
        if (Config.Plugin.Solr.SKIP_ADMIN_REQUEST && request.getPath().startsWith(SKIP_ADMIN_PREF)) {
            return ret;
        }

        if (ContextManager.isActive()) {
            SolrQueryResponse response = (SolrQueryResponse) allArguments[1];
            Exception exp = response.getException();
            if (exp != null) {
                ContextManager.activeSpan().errorOccurred().log(exp);
            }
            ContextManager.stopSpan();
        }
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
        if (ContextManager.isActive()) {
            ContextManager.activeSpan().errorOccurred().log(t);
        }
    }
}