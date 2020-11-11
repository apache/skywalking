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

import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
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
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.params.UpdateParams;
import org.apache.solr.common.util.NamedList;

public class SolrClientInterceptor implements InstanceMethodsAroundInterceptor, InstanceConstructorInterceptor {
    private static final String DB_TYPE = "Solr";

    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        SolrjInstance instance = new SolrjInstance();
        HttpSolrClient client = (HttpSolrClient) objInst;

        try {
            URL url = new URL(client.getBaseURL());
            instance.setRemotePeer(url.getHost() + ":" + url.getPort());

            String path = url.getPath();
            int idx = path.lastIndexOf('/');
            if (idx > 0) {
                instance.setCollection(path.substring(idx + 1));
            }
        } catch (MalformedURLException ignore) {
        }
        objInst.setSkyWalkingDynamicField(instance);
    }

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        SolrRequest<?> request = (SolrRequest<?>) allArguments[0];
        SolrjInstance instance = (SolrjInstance) objInst.getSkyWalkingDynamicField();

        SolrParams params = getParams(request.getParams());
        String collection = getCollection(instance, allArguments[2]);

        if ("/update".equals(request.getPath())) {
            AbstractUpdateRequest update = (AbstractUpdateRequest) request;

            AbstractUpdateRequest.ACTION action = update.getAction();
            if (action == null) {
                if (update instanceof UpdateRequest) {
                    AbstractSpan span = null;

                    UpdateRequest ur = (UpdateRequest) update;
                    List<SolrInputDocument> documents = ur.getDocuments();
                    if (documents == null) {
                        String actionName = "DELETE_BY_IDS";

                        List<String> deleteBy = ur.getDeleteById();
                        if (deleteBy == null) {
                            actionName = "DELETE_BY_QUERY";
                            deleteBy = ur.getDeleteQuery();
                        }
                        if (deleteBy == null) {
                            deleteBy = new ArrayList<>();
                        }
                        String operator = getOperatorNameWithAction(collection, request.getPath(), actionName);
                        span = getSpan(operator, instance.getRemotePeer());
                        if (SolrJPluginConfig.Plugin.SolrJ.TRACE_STATEMENT) {
                            span.tag(Tags.DB_STATEMENT, deleteBy.toString());
                        }
                    } else {
                        String operator = getOperatorNameWithAction(collection, request.getPath(), "ADD");
                        span = getSpan(operator, instance.getRemotePeer());
                        if (SolrJPluginConfig.Plugin.SolrJ.TRACE_STATEMENT) {
                            span.tag(SolrjTags.TAG_DOCS_SIZE, String.valueOf(documents.size()));
                        }
                    }
                    if (SolrJPluginConfig.Plugin.SolrJ.TRACE_OPS_PARAMS) {
                        span.tag(SolrjTags.TAG_COMMIT_WITHIN, String.valueOf(ur.getCommitWithin()));
                    }
                } else {
                    getSpan(getOperatorName(collection, request.getPath()), instance.getRemotePeer());
                }
            } else {
                String operator = getOperatorNameWithAction(collection, request.getPath(), action.name());
                AbstractSpan span = getSpan(operator, instance.getRemotePeer());

                if (SolrJPluginConfig.Plugin.SolrJ.TRACE_OPS_PARAMS) {
                    if (action == AbstractUpdateRequest.ACTION.COMMIT) {
                        span.tag(SolrjTags.TAG_SOFT_COMMIT, params.get(UpdateParams.SOFT_COMMIT, ""));
                    } else {
                        span.tag(
                            SolrjTags.TAG_MAX_OPTIMIZE_SEGMENTS, params.get(UpdateParams.MAX_OPTIMIZE_SEGMENTS, "1"));
                    }
                }
            }
        } else if (request instanceof QueryRequest) {
            AbstractSpan span = getSpan(getOperatorName(collection, request.getPath()), instance.getRemotePeer());

            span.tag(SolrjTags.TAG_START, params.get(CommonParams.START, "0"));
            span.tag(SolrjTags.TAG_QT, params.get(CommonParams.QT, request.getPath()));

            if (SolrJPluginConfig.Plugin.SolrJ.TRACE_STATEMENT) {
                span.tag(Tags.DB_STATEMENT, toQueryString(params));
            }
        } else {
            getSpan(getOperatorName(collection, request.getPath()), instance.getRemotePeer());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret) throws Throwable {
        if (!ContextManager.isActive()) {
            return ret;
        }

        AbstractSpan span = ContextManager.activeSpan();
        if (ret != null) {
            NamedList<Object> result = (NamedList<Object>) ret;
            NamedList<Object> header = (NamedList<Object>) result.get("responseHeader");

            if (header != null) {
                span.tag(SolrjTags.TAG_Q_TIME, String.valueOf(header.get("QTime")));
            }
            SolrDocumentList list = (SolrDocumentList) result.get("response");
            if (list != null) {
                span.tag(SolrjTags.TAG_NUM_FOUND, String.valueOf(list.getNumFound()));
            }
        }

        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
        if (ContextManager.isActive()) {
            AbstractSpan span = ContextManager.activeSpan();
            int code = 500;
            if (t instanceof SolrException) {
                code = ((SolrException) t).code();
            }
            span.tag(SolrjTags.TAG_STATUS, String.valueOf(code));
            span.log(t);
        }
    }

    private static final AbstractSpan getSpan(String operatorName, String remotePeer) {
        return ContextManager.createExitSpan(operatorName, remotePeer)
                             .setComponent(ComponentsDefine.SOLRJ)
                             .setLayer(SpanLayer.DB)
                             .tag(Tags.DB_TYPE, DB_TYPE);
    }

    private static final String getOperatorNameWithAction(String collection, String path, String action) {
        return "solrJ/" + collection + path + "/" + action;
    }

    private static final String getOperatorName(String collection, String path) {
        return "solrJ/" + collection + path;
    }

    private static final String getCollection(SolrjInstance instance, Object argument) {
        if (null == argument) {
            return instance.getCollection();
        }
        return String.valueOf(argument);
    }

    private static final SolrParams getParams(SolrParams params) {
        if (params == null) {
            return new ModifiableSolrParams();
        }
        return params;
    }

    private static final String toQueryString(SolrParams params) {
        final StringBuilder sb = new StringBuilder(128);
        boolean first = true;
        for (final Iterator<String> it = params.getParameterNamesIterator(); it.hasNext(); ) {
            final String name = it.next();
            for (String val : params.getParams(name)) {
                sb.append(first ? '?' : '&').append(name).append('=').append(val);
                first = false;
            }
        }
        return sb.toString();
    }
}
