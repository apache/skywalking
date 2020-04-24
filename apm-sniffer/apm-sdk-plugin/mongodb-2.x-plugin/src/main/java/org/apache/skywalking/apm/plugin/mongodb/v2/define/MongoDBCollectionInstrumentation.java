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

package org.apache.skywalking.apm.plugin.mongodb.v2.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;

import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static org.apache.skywalking.apm.agent.core.plugin.bytebuddy.ArgumentTypeNameMatch.takesArgumentWithType;
import static org.apache.skywalking.apm.agent.core.plugin.match.NameMatch.byName;

/**
 * {@link MongoDBCollectionInstrumentation} define that the MongoDB Java Driver 2.13.x-2.14.x plugin intercepts the
 * following methods in the {@link com.mongodb.DBCollection}class: 1. aggregate 2. findAndModify 3. getCount
 * <p>
 * 4. drop 5. dropIndexes 6. rename 7. group 8. distinct 9. mapReduce
 */
public class MongoDBCollectionInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    private static final String ENHANCE_CLASS = "com.mongodb.DBCollection";

    private static final String MONGDB_METHOD_INTERCET_CLASS = "org.apache.skywalking.apm.plugin.mongodb.v2.MongoDBCollectionMethodInterceptor";

    @Override
    public ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[] {
            new ConstructorInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getConstructorMatcher() {
                    return any();
                }

                @Override
                public String getConstructorInterceptor() {
                    return MONGDB_METHOD_INTERCET_CLASS;
                }
            }
        };
    }

    @Override
    public InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[] {

            new InterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("aggregate").and(takesArgumentWithType(1, "com.mongodb.ReadPreference"));
                }
            },

            new InterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("findAndModify").and(takesArguments(9));
                }
            },
            new InterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("getCount").and(takesArgumentWithType(6, "java.util.concurrent.TimeUnit"));
                }
            },
            new InterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("drop");
                }

            },
            new InterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("dropIndexes");
                }

            },
            new InterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("rename").and(takesArgumentWithType(1, "boolean"));
                }

            },
            new InterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("group").and(takesArgumentWithType(1, "boolean"));
                }

            },
            new InterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("group").and(takesArgumentWithType(1, "com.mongodb.DBObject"));
                }
            },
            new InterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("distinct").and(takesArgumentWithType(2, "com.mongodb.ReadPreference"));
                }
            },
            new InterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("mapReduce").and(takesArgumentWithType(0, "com.mongodb.MapReduceCommand"));
                }
            },
            new InterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("mapReduce").and(takesArgumentWithType(0, "com.mongodb.DBObject"));
                }
            },
            new InterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("explainAggregate");
                }
            },

            };
    }

    @Override
    protected ClassMatch enhanceClass() {
        return byName(ENHANCE_CLASS);
    }

    @Override
    protected String[] witnessClasses() {
        /**
         * @see {@link com.mongodb.tools.ConnectionPoolStat}
         */
        return new String[] {
            "com.mongodb.tools.ConnectionPoolStat"
        };
    }

}
