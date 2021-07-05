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

package org.apache.skywalking.apm.plugin.ehcache.v2.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static net.bytebuddy.matcher.ElementMatchers.isPrivate;
import static org.apache.skywalking.apm.agent.core.plugin.match.NameMatch.byName;

/**
 * {@link EhcachePluginInstrumentation} enhance @{@link net.sf.ehcache.Cache}
 */
public class EhcachePluginInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    public static final String INTERCEPT_CLASS = "net.sf.ehcache.Cache";
    public static final String CONSTRUCTOR_CLASS_INTERCEPT_CLASS = "org.apache.skywalking.apm.plugin.ehcache.v2.EhcacheConstructorInterceptor";
    public static final String PRIVATE_CONSTRUCTOR_CLASS_INTERCEPT_CLASS = "org.apache.skywalking.apm.plugin.ehcache.v2.EhcachePrivateConstructorInterceptor";

    // get and put value
    public static final String PUT_CACHE_ENHANCE_METHOD = "put";
    public static final String GET_CACHE_ENHANCE_METHOD = "get";
    public static final String GET_QUIET_CACHE_ENHANCE_METHOD = "getQuiet";
    public static final String REMOVE_CACHE_ENHANCE_METHOD = "remove";
    public static final String REMOVE_AND_RETURN_ELEMENT_CACHE_ENHANCE_METHOD = "removeAndReturnElement";
    public static final String REPLACE_CACHE_ENHANCE_METHOD = "replace";
    public static final String REMOVE_QUIET_CACHE_ENHANCE_METHOD = "removeQuiet";
    public static final String REMOVE_WITH_WRITE_CACHE_INHANCE_METHOD = "removeWithWriter";
    public static final String REMOVE_ELEMENT_CACHE_ENHANCE_METHOD = "removeElement";
    public static final String REMOVE_ALL_CACHE_INHANCE_METHOD = "removeAll";
    public static final String PUT_ALL_CACHE_ENHANCE_METHOD = "putAll";
    public static final String PUT_WITH_WRITE_CACHE_ENHANCE_METHOD = "putWithWriter";
    public static final String PUT_QUITE_CACHE_ENHANCE_METHOD = "putQuiet";
    public static final String GET_WITH_LOADER_CACHE_ENHANCE_METHOD = "getWithLoader";
    public static final String PUT_IF_ABSENT_CACHE_ENHANCE_METHOD = "putIfAbsent";
    public static final String GET_ALL_CACHE_ENHANCE_METHOD = "getAll";
    public static final String LOAD_ALL_CACHE_ENHANCE_METHOD = "loadAll";
    public static final String GET_ALL_WITH_LOADER_CACHE_ENHANCE_METHOD = "getAllWithLoader";
    public static final String OPERATE_ELEMENT_CACHE_INTERCEPTOR_CLASS = "org.apache.skywalking.apm.plugin.ehcache.v2.EhcacheOperateElementInterceptor";
    public static final String OPERATE_OBJECT_CACHE_INTERCEPTOR_CLASS = "org.apache.skywalking.apm.plugin.ehcache.v2.EhcacheOperateObjectInterceptor";
    public static final String OPERATE_ALL_CACHE_INTERCEPTOR_CLASS = "org.apache.skywalking.apm.plugin.ehcache.v2.EhcacheOperateAllInterceptor";

    // lock and release
    public static final String LOCK_ENHANCE_METHOD_SUFFIX = "LockOnKey";
    public static final String WRITE_LOCK_TRY_ENHANCE_METHOD = "tryWrite" + LOCK_ENHANCE_METHOD_SUFFIX;
    public static final String WRITE_LOCK_RELEASE_ENHANCE_METHOD = "releaseWrite" + LOCK_ENHANCE_METHOD_SUFFIX;
    public static final String READ_LOCK_TRY_ENHANCE_METHOD = "tryRead" + LOCK_ENHANCE_METHOD_SUFFIX;
    public static final String READ_LOCK_RELEASE_ENHANCE_METHOD = "releaseRead" + LOCK_ENHANCE_METHOD_SUFFIX;
    public static final String READ_WRITE_LOCK_INTERCEPTOR_CLASS = "org.apache.skywalking.apm.plugin.ehcache.v2.EhcacheLockInterceptor";

    // cache name
    public static final String CACHE_NAME_ENHANCE_METHOD = "setName";
    public static final String CACHE_NAME_INTERCEPTOR_CLASS = "org.apache.skywalking.apm.plugin.ehcache.v2.EhcacheCacheNameInterceptor";

    @Override
    public ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[] {
            new ConstructorInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getConstructorMatcher() {
                    return takesArgument(0, named("net.sf.ehcache.config.CacheConfiguration"));
                }

                @Override
                public String getConstructorInterceptor() {
                    return CONSTRUCTOR_CLASS_INTERCEPT_CLASS;
                }
            },
            new ConstructorInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getConstructorMatcher() {
                    return isPrivate().and(takesArgument(0, named("net.sf.ehcache.Cache")));
                }

                @Override
                public String getConstructorInterceptor() {
                    return PRIVATE_CONSTRUCTOR_CLASS_INTERCEPT_CLASS;
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
                    return named(CACHE_NAME_ENHANCE_METHOD).and(takesArgument(0, String.class));
                }

                @Override
                public String getMethodsInterceptor() {
                    return CACHE_NAME_INTERCEPTOR_CLASS;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }

            },
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(GET_WITH_LOADER_CACHE_ENHANCE_METHOD).or(named(GET_CACHE_ENHANCE_METHOD).and(takesArgument(0, Object.class)))
                                                                      .or(named(GET_QUIET_CACHE_ENHANCE_METHOD).and(takesArgument(0, Object.class)))
                                                                      .or(named(REMOVE_CACHE_ENHANCE_METHOD).and(takesArguments(2))
                                                                                                            .and(takesArgument(0, Object.class)))
                                                                      .or(named(REMOVE_AND_RETURN_ELEMENT_CACHE_ENHANCE_METHOD))
                                                                      .or(named(REMOVE_QUIET_CACHE_ENHANCE_METHOD))
                                                                      .or(named(REMOVE_WITH_WRITE_CACHE_INHANCE_METHOD));
                }

                @Override
                public String getMethodsInterceptor() {
                    return OPERATE_OBJECT_CACHE_INTERCEPTOR_CLASS;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }

            },
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(PUT_WITH_WRITE_CACHE_ENHANCE_METHOD).or(named(PUT_QUITE_CACHE_ENHANCE_METHOD))
                                                                     .or(named(REMOVE_ELEMENT_CACHE_ENHANCE_METHOD))
                                                                     .or(named(REPLACE_CACHE_ENHANCE_METHOD))
                                                                     .or(named(PUT_IF_ABSENT_CACHE_ENHANCE_METHOD).and(takesArguments(2)))
                                                                     .or(named(PUT_CACHE_ENHANCE_METHOD).and(takesArguments(2)));
                }

                @Override
                public String getMethodsInterceptor() {
                    return OPERATE_ELEMENT_CACHE_INTERCEPTOR_CLASS;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            },
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(REMOVE_ALL_CACHE_INHANCE_METHOD).and(takesArguments(1).and(takesArgument(0, Boolean.TYPE)))
                                                                 .or(named(REMOVE_ALL_CACHE_INHANCE_METHOD).and(takesArguments(2)))
                                                                 .or(named(PUT_ALL_CACHE_ENHANCE_METHOD).and(takesArguments(2)))
                                                                 .or(named(GET_ALL_WITH_LOADER_CACHE_ENHANCE_METHOD))
                                                                 .or(named(GET_ALL_CACHE_ENHANCE_METHOD))
                                                                 .or(named(LOAD_ALL_CACHE_ENHANCE_METHOD));
                }

                @Override
                public String getMethodsInterceptor() {
                    return OPERATE_ALL_CACHE_INTERCEPTOR_CLASS;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            },
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(READ_LOCK_RELEASE_ENHANCE_METHOD).or(named(READ_LOCK_TRY_ENHANCE_METHOD).or(named(WRITE_LOCK_RELEASE_ENHANCE_METHOD))
                                                                                                         .or(named(WRITE_LOCK_TRY_ENHANCE_METHOD)));
                }

                @Override
                public String getMethodsInterceptor() {
                    return READ_WRITE_LOCK_INTERCEPTOR_CLASS;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            }
        };
    }

    @Override
    protected ClassMatch enhanceClass() {
        return byName(INTERCEPT_CLASS);
    }
}
