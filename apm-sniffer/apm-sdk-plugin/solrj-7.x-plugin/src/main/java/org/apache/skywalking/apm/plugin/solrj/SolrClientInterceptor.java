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
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.plugin.solrj.commons.SolrjInstance;
import org.apache.skywalking.apm.plugin.solrj.commons.SolrjTags;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.request.AbstractUpdateRequest;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.client.solrj.request.V2Request;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.params.UpdateParams;
import org.apache.solr.common.util.NamedList;

import java.lang.reflect.Method;
import java.util.List;
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
//    	HttpSolrClient#request(SolrRequest, ResponseParser, String):NamedList
//    	HttpSolrClient client = (HttpSolrClient) objInst;
        SolrRequest<?> request = (SolrRequest<?>) allArguments[0];
        SolrjInstance instance = (SolrjInstance) objInst.getSkyWalkingDynamicField();

        String collection = instance.getCollection();
        Object ocollection = allArguments[2];
        if (ocollection != null) {
            collection = ocollection.toString();
        }
        if (collection == null || "".equals(collection)) {
            collection = "<empty>";
        }

        // solr/collection/select
        String operatorName = String.format("solrJ/%s%s", collection, request.getPath());
        AbstractSpan span = ContextManager.createExitSpan(operatorName, instance.getRemotePeer())
                .setComponent(ComponentsDefine.SOLRJ)
                .setLayer(SpanLayer.DB);

        SolrParams params = request.getParams();
        if (params == null) {
            params = new ModifiableSolrParams();
        }
        if (request instanceof AbstractUpdateRequest) {
            span.tag(SolrjTags.TAG_QT, params.get(CommonParams.QT, "/update"));
            AbstractUpdateRequest update = (AbstractUpdateRequest) request;
            String action = "ADD";
            if (update.getAction() != null) {
                action = update.getAction().name();

                if (update.getAction() == AbstractUpdateRequest.ACTION.COMMIT) {
                    span.tag(SolrjTags.TAG_COMMIT, params.get(UpdateParams.COMMIT, "true" ));
                    span.tag(SolrjTags.TAG_SOFT_COMMIT, params.get(UpdateParams.SOFT_COMMIT, "" ));
                }
                else if (update.getAction() == AbstractUpdateRequest.ACTION.OPTIMIZE) {
                    span.tag(SolrjTags.TAG_OPTIMIZE, params.get(UpdateParams.OPTIMIZE, "true" ));
                    span.tag(SolrjTags.TAG_MAX_OPTIMIZE_SEGMENTS, params.get(UpdateParams.MAX_OPTIMIZE_SEGMENTS, "1" ));
                } else {
                    if (update instanceof UpdateRequest) {
                        UpdateRequest ur = (UpdateRequest) update;
                        List<SolrInputDocument> documents = ur.getDocuments();
                        if (documents == null) {
                            List<String> deleteById = ur.getDeleteById();
                            if (deleteById != null && !deleteById.isEmpty()) {
                                span.tag(SolrjTags.TAG_DELETE_BY_ID, deleteById.toString());
                            }
                            List<String> deleteQuery = ur.getDeleteQuery();
                            if (deleteQuery != null && !deleteQuery.isEmpty()) {
                                span.tag(SolrjTags.TAG_DELETE_BY_QUERY, deleteQuery.toString());
                            }
                        } else {
                            span.tag(SolrjTags.TAG_DOCS_SIZE, String.valueOf(documents.size()));
                        }
                    }
                    span.tag(SolrjTags.TAG_COMMIT_WITHIN, String.valueOf(update.getCommitWithin()));
                }
            }

            span.tag(SolrjTags.TAG_ACTION, action);
        } else if (request instanceof QueryRequest) {
            span.tag(SolrjTags.TAG_QT, params.get(CommonParams.QT, "/select"));
        }

        span.tag(SolrjTags.TAG_PATH, request.getPath());
        span.tag(SolrjTags.TAG_COLLECTION, collection);
        span.tag(SolrjTags.TAG_METHOD, request.getMethod().name());
        
        ContextManager.getRuntimeContext().put("instance", instance);
        ContextManager.getRuntimeContext().put("request.start", Long.valueOf(System.currentTimeMillis()));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
                              Class<?>[] argumentsTypes, Object ret) throws Throwable {
        if (!ContextManager.isActive()) return ret;

    	Long qStart = ContextManager.getRuntimeContext().get("request.start", Long.class);
    	long elapse = System.currentTimeMillis() - qStart.longValue();

        AbstractSpan span = ContextManager.activeSpan();
        if (ret instanceof SolrDocumentList) {
            SolrDocumentList list = (SolrDocumentList) ret;
            span.tag(SolrjTags.TAG_NUM_FOUND, String.valueOf(list.getNumFound()));
        }
        NamedList<Object> response = (NamedList<Object>) ret;
        if (response != null) {
            NamedList<Object> header = (NamedList<Object>) response.get("responseHeader");
            if (header != null) { // common
                span.tag(SolrjTags.TAG_STATUS, header.get("status").toString());
                span.tag(SolrjTags.TAG_Q_TIME, header.get("QTime").toString());
            }
            SolrDocumentList list = (SolrDocumentList) response.get("response");
            if (list != null) { // query
                span.tag(SolrjTags.TAG_NUM_FOUND, String.valueOf(list.getNumFound()));
            }
        }
        SolrjInstance instance = ContextManager.getRuntimeContext().get("instance", SolrjInstance.class);
        SolrjTags.addHttpResponse(span, instance);
        SolrjTags.addElapseTime(span, elapse);

        ContextManager.getRuntimeContext().remove("instance");
        ContextManager.getRuntimeContext().remove("request.start");
        ContextManager.stopSpan();

        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Throwable t) {
        ContextManager.activeSpan().errorOccurred().log(t);
    }
}
