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
package org.apache.skywalking.apm.plugin.solrj;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.StringTag;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.plugin.solrj.commons.SolrjInstance;
import org.apache.skywalking.apm.plugin.solrj.commons.SolrjTags;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.request.AbstractUpdateRequest;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;

import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//
public class SolrClientInterceptor implements InstanceMethodsAroundInterceptor, InstanceConstructorInterceptor {
	private static final Pattern URL_REGEX = Pattern.compile("(?<pref>http(s)?://)*(?<domain>[\\w_.\\-\\d]+(:\\d+)?)?/(?<path>solr/(?<collection>[\\w_]+))?(/.*)?");

    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
//      HttpSolrClient(HttpSolrClient.Builder builder)
    	SolrjInstance instance = new SolrjInstance();
        HttpSolrClient client = (HttpSolrClient) objInst;

        Matcher matcher = URL_REGEX.matcher(client.getBaseURL());
        if (matcher.find()) {
            instance.setRemotePeer(matcher.group(3));
            if (matcher.group(6) != null)
                instance.setCollection(matcher.group(6));
        }
        objInst.setSkyWalkingDynamicField(instance);
    }

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
//    	HttpSolrClient#request(SolrRequest, String):NamedList
//    	HttpSolrClient client = (HttpSolrClient) objInst;
        SolrRequest<?> request = (SolrRequest<?>) allArguments[0];
        SolrjInstance instance = (SolrjInstance) objInst.getSkyWalkingDynamicField();

        String collection = instance.getCollection();
        Object ocollection = allArguments[1];
        if (ocollection != null) {
            collection = ocollection.toString();
        }
        if (collection == null || "".equals(collection)) {
            collection = "<empty>";
        }
        
        // solr/collection/select
        String operatorName = String.format("solrJ/%s%s", collection, request.getPath());
        AbstractSpan span = ContextManager.createExitSpan(operatorName, instance.getRemotePeer())
                .setLayer(SpanLayer.DB);
        SolrParams params = request.getParams();
        span.tag(new StringTag(12, "qt"), params.get(CommonParams.QT, "/select"));

        if (request instanceof AbstractUpdateRequest) {
            AbstractUpdateRequest update = (AbstractUpdateRequest) request;
            String action = "add";
            if (update.getAction() != null) {
                action = update.getAction().name();
            }

            span.tag(SolrjTags.ACTION, action);
            span.tag(SolrjTags.COMMIT_WITHIN, String.valueOf(update.getCommitWithin()));
        }

        span.tag(SolrjTags.PATH, request.getPath());
        span.tag(SolrjTags.COLLECTION, collection);
        span.tag(SolrjTags.METHOD, request.getMethod().name());
        
        ContextManager.getRuntimeContext().put("instance", instance);
        ContextManager.getRuntimeContext().put("request.start", (Long) System.currentTimeMillis());
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
                              Class<?>[] argumentsTypes, Object ret) throws Throwable {
    	Long qstart = ContextManager.getRuntimeContext().get("request.start", Long.class);
    	long elapse = System.currentTimeMillis() - qstart.longValue();
    	
        AbstractSpan span = ContextManager.activeSpan();
        if (ret instanceof SolrDocumentList) {
            SolrDocumentList list = (SolrDocumentList) ret;
            span.tag(SolrjTags.NUM_FOUND, String.valueOf(list.getNumFound()));
        } else {
			NamedList<Object> response = (NamedList<Object>) ret;
            if (response != null) {
                NamedList<Object> header = (NamedList<Object>) response.get("responseHeader");
                if (header != null) { // common
                    span.tag(SolrjTags.STATUS, header.get("status").toString());
                    span.tag(SolrjTags.Q_TIME, header.get("QTime").toString());
                }
                SolrDocumentList list = (SolrDocumentList) response.get("response");
                if (list != null) { // query
                    span.tag(SolrjTags.NUM_FOUND, String.valueOf(list.getNumFound()));
                }
            }
        }
        SolrjInstance instance = ContextManager.getRuntimeContext().get("instance", SolrjInstance.class);
        
        SolrjTags.addHttpEntity(span, instance);
        SolrjTags.addElapseTime(span, elapse);
        ContextManager.stopSpan();
        
//        ContextManager.getRuntimeContext().remove("instance");
//        ContextManager.getRuntimeContext().remove("request.start");
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Throwable t) {
    }
}
