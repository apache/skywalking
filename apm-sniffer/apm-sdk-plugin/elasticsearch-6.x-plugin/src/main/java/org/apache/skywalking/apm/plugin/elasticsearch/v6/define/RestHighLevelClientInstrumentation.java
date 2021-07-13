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

package org.apache.skywalking.apm.plugin.elasticsearch.v6.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.StaticMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.apache.skywalking.apm.plugin.elasticsearch.v6.interceptor.Constants;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static org.apache.skywalking.apm.agent.core.plugin.bytebuddy.ArgumentTypeNameMatch.takesArgumentWithType;
import static org.apache.skywalking.apm.agent.core.plugin.match.NameMatch.byName;

/**
 * {@link RestHighLevelClientInstrumentation} enhance the constructor method without argument in
 * <code>org.elasticsearch.client.RestHighLevelClient</code> by <code>org.apache.skywalking.apm.plugin.elasticsearch.v6.interceptor.RestHighLevelClientConInterceptor</code>
 * also enhance the <code>performRequestAndParseEntity</code> method in <code>org.elasticsearch.client.RestHighLevelClient</code>
 * by <code>org.apache.skywalking.apm.plugin.elasticsearch.v6.interceptor.IndicesClientCreateMethodsInterceptor</code>,
 * also enhance the <code>get getAsync search searchAsync index indexAsync update updateAsync</code> method in
 * <code>org.apache.skywalking.apm.plugin.elasticsearch.v6.interceptor.RestHighLevelClientGetMethodsInterceptor
 * org.apache.skywalking.apm.plugin.elasticsearch.v6.interceptor.RestHighLevelClientSearchMethodsInterceptor
 * org.apache.skywalking.apm.plugin.elasticsearch.v6.interceptor.RestHighLevelClientIndexMethodsInterceptor
 * org.apache.skywalking.apm.plugin.elasticsearch.v6.interceptor.RestHighLevelClientUpdateMethodsInterceptor</code>,
 * also enhance the <code>indices</code> method in <code>org.elasticsearch.client.RestHighLevelClient</code> by
 * <code>org.apache.skywalking.apm.plugin.elasticsearch.v6.interceptor.RestHighLevelClientIndicesMethodsInterceptor</code>
 */
public class RestHighLevelClientInstrumentation extends ClassEnhancePluginDefine {

    public static final String ENHANCE_CLASS = "org.elasticsearch.client.RestHighLevelClient";

    @Override
    protected ClassMatch enhanceClass() {
        return byName(ENHANCE_CLASS);
    }

    @Override
    public ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[] {
            new ConstructorInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getConstructorMatcher() {
                    return takesArguments(1);
                }

                @Override
                public String getConstructorInterceptor() {
                    return Constants.REST_HIGH_LEVEL_CLIENT_CON_INTERCEPTOR;
                }
            }
        };
    }

    @Override
    public InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[] {
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("performRequestAndParseEntity").and(takesArgumentWithType(0, "org.elasticsearch.client.indices.CreateIndexRequest"));
                }

                @Override
                public String getMethodsInterceptor() {
                    return Constants.INDICES_CLIENT_CREATE_METHODS_INTERCEPTOR;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            },
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("get").or(named("getAsync"));
                }

                @Override
                public String getMethodsInterceptor() {
                    return Constants.REST_HIGH_LEVEL_CLIENT_GET_METHODS_INTERCEPTOR;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            },
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("search").or(named("searchAsync"));
                }

                @Override
                public String getMethodsInterceptor() {
                    return Constants.REST_HIGH_LEVEL_CLIENT_SEARCH_METHODS_INTERCEPTOR;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            },
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("index").or(named("indexAsync"));
                }

                @Override
                public String getMethodsInterceptor() {
                    return Constants.REST_HIGH_LEVEL_CLIENT_INDEX_METHODS_INTERCEPTOR;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            },
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("update").or(named("updateAsync"));
                }

                @Override
                public String getMethodsInterceptor() {
                    return Constants.REST_HIGH_LEVEL_CLIENT_UPDATE_METHODS_INTERCEPTOR;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            },
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("indices");
                }

                @Override
                public String getMethodsInterceptor() {
                    return Constants.REST_HIGH_LEVEL_CLIENT_INDICES_METHODS_INTERCEPTOR;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            },
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("cluster");
                }

                @Override
                public String getMethodsInterceptor() {
                    return Constants.REST_HIGH_LEVEL_CLIENT_CLUSTER_METHODS_INTERCEPTOR;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            },
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("scroll").or(named("scrollAsync"));
                }

                @Override
                public String getMethodsInterceptor() {
                    return Constants.REST_HIGH_LEVEL_CLIENT_SEARCH_SCROLL_METHODS_INTERCEPTOR;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            },
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("searchTemplate").or(named("searchTemplateAsync"));
                }

                @Override
                public String getMethodsInterceptor() {
                    return Constants.REST_HIGH_LEVEL_CLIENT_SEARCH_TEMPLATE_METHODS_INTERCEPTOR;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            },
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("clearScroll").or(named("clearScrollAsync"));
                }

                @Override
                public String getMethodsInterceptor() {
                    return Constants.REST_HIGH_LEVEL_CLIENT_CLEAR_SCROLL_METHODS_INTERCEPTOR;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            },
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("deleteByQuery").or(named("deleteByQueryAsync"));
                }

                @Override
                public String getMethodsInterceptor() {
                    return Constants.REST_HIGH_LEVEL_CLIENT_DELETE_BY_QUERY_METHODS_INTERCEPTOR;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            }
        };
    }

    @Override
    public StaticMethodsInterceptPoint[] getStaticMethodsInterceptPoints() {
        return new StaticMethodsInterceptPoint[0];
    }

    @Override
    protected String[] witnessClasses() {
        return new String[] {Constants.TASK_TRANSPORT_CHANNEL_WITNESS_CLASSES};
    }
}
