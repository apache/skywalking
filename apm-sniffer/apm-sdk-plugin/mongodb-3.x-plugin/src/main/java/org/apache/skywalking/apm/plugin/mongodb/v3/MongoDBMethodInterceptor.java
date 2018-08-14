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


package org.apache.skywalking.apm.plugin.mongodb.v3;

import com.mongodb.ReadPreference;
import com.mongodb.ServerAddress;
import com.mongodb.bulk.DeleteRequest;
import com.mongodb.bulk.InsertRequest;
import com.mongodb.bulk.UpdateRequest;
import com.mongodb.bulk.WriteRequest;
import com.mongodb.connection.Cluster;
import com.mongodb.connection.ServerDescription;
import com.mongodb.operation.CountOperation;
import com.mongodb.operation.CreateCollectionOperation;
import com.mongodb.operation.CreateIndexesOperation;
import com.mongodb.operation.CreateViewOperation;
import com.mongodb.operation.DeleteOperation;
import com.mongodb.operation.DistinctOperation;
import com.mongodb.operation.FindAndDeleteOperation;
import com.mongodb.operation.FindAndReplaceOperation;
import com.mongodb.operation.FindAndUpdateOperation;
import com.mongodb.operation.FindOperation;
import com.mongodb.operation.GroupOperation;
import com.mongodb.operation.InsertOperation;
import com.mongodb.operation.ListCollectionsOperation;
import com.mongodb.operation.MapReduceToCollectionOperation;
import com.mongodb.operation.MapReduceWithInlineResultsOperation;
import com.mongodb.operation.MixedBulkWriteOperation;
import com.mongodb.operation.ReadOperation;
import com.mongodb.operation.UpdateOperation;
import com.mongodb.operation.WriteOperation;
import java.lang.reflect.Method;
import java.util.List;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.bson.BsonDocument;

/**
 * {@link MongoDBMethodInterceptor} intercept method of {@link com.mongodb.Mongo#execute(ReadOperation, ReadPreference)}
 * or {@link com.mongodb.Mongo#execute(WriteOperation)}. record the mongoDB host, operation name and the key of the
 * operation.
 *
 * @author baiyang
 */
public class MongoDBMethodInterceptor implements InstanceMethodsAroundInterceptor, InstanceConstructorInterceptor {

    private static final String DB_TYPE = "MongoDB";

    private static final String MONGO_DB_OP_PREFIX = "MongoDB/";

    private static final int FILTER_LENGTH_LIMIT = 256;

    private static final String EMPTY = "";

    /**
     * Convert ReadOperation interface or WriteOperation interface to the implementation class. Get the method name and
     * filter info.
     */
    @SuppressWarnings("rawtypes")
    private String getTraceParam(Object obj) {
        if (obj instanceof CountOperation) {
            BsonDocument filter = ((CountOperation)obj).getFilter();
            return limitFilter(filter.toString());
        } else if (obj instanceof DistinctOperation) {
            BsonDocument filter = ((DistinctOperation)obj).getFilter();
            return limitFilter(filter.toString());
        } else if (obj instanceof FindOperation) {
            BsonDocument filter = ((FindOperation)obj).getFilter();
            return limitFilter(filter.toString());
        } else if (obj instanceof GroupOperation) {
            BsonDocument filter = ((GroupOperation)obj).getFilter();
            return limitFilter(filter.toString());
        } else if (obj instanceof ListCollectionsOperation) {
            BsonDocument filter = ((ListCollectionsOperation)obj).getFilter();
            return limitFilter(filter.toString());
        } else if (obj instanceof MapReduceWithInlineResultsOperation) {
            BsonDocument filter = ((ListCollectionsOperation)obj).getFilter();
            return limitFilter(filter.toString());
        } else if (obj instanceof DeleteOperation) {
            List<DeleteRequest> writeRequestList = ((DeleteOperation)obj).getDeleteRequests();
            return getFilter(writeRequestList);
        } else if (obj instanceof InsertOperation) {
            List<InsertRequest> writeRequestList = ((InsertOperation)obj).getInsertRequests();
            return getFilter(writeRequestList);
        } else if (obj instanceof UpdateOperation) {
            List<UpdateRequest> writeRequestList = ((UpdateOperation)obj).getUpdateRequests();
            return getFilter(writeRequestList);
        } else if (obj instanceof CreateCollectionOperation) {
            String filter = ((CreateCollectionOperation)obj).getCollectionName();
            return limitFilter(filter);
        } else if (obj instanceof CreateIndexesOperation) {
            List<String> filter = ((CreateIndexesOperation)obj).getIndexNames();
            return limitFilter(filter.toString());
        } else if (obj instanceof CreateViewOperation) {
            String filter = ((CreateViewOperation)obj).getViewName();
            return limitFilter(filter);
        } else if (obj instanceof FindAndDeleteOperation) {
            BsonDocument filter = ((FindAndDeleteOperation)obj).getFilter();
            return limitFilter(filter.toString());
        } else if (obj instanceof FindAndReplaceOperation) {
            BsonDocument filter = ((FindAndReplaceOperation)obj).getFilter();
            return limitFilter(filter.toString());
        } else if (obj instanceof FindAndUpdateOperation) {
            BsonDocument filter = ((FindAndUpdateOperation)obj).getFilter();
            return limitFilter(filter.toString());
        } else if (obj instanceof MapReduceToCollectionOperation) {
            BsonDocument filter = ((MapReduceToCollectionOperation)obj).getFilter();
            return limitFilter(filter.toString());
        } else if (obj instanceof MixedBulkWriteOperation) {
            List<? extends WriteRequest> writeRequestList = ((MixedBulkWriteOperation)obj).getWriteRequests();
            return getFilter(writeRequestList);
        } else {
            return EMPTY;
        }
    }

    private String getFilter(List<? extends WriteRequest> writeRequestList) {
        StringBuilder params = new StringBuilder();
        for (WriteRequest request : writeRequestList) {
            if (request instanceof InsertRequest) {
                params.append(((InsertRequest)request).getDocument().toString()).append(",");
            } else if (request instanceof DeleteRequest) {
                params.append(((DeleteRequest)request).getFilter()).append(",");
            } else if (request instanceof UpdateRequest) {
                params.append(((UpdateRequest)request).getFilter()).append(",");
            }
            if (params.length() > FILTER_LENGTH_LIMIT) {
                params.append("...");
                break;
            }
        }
        return params.toString();
    }

    private String limitFilter(String filter) {
        final StringBuilder params = new StringBuilder();
        if (filter.length() > FILTER_LENGTH_LIMIT) {
            return params.append(filter.substring(0, FILTER_LENGTH_LIMIT)).append("...").toString();
        } else {
            return filter;
        }
    }

    @Override public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
        Object[] arguments = allArguments;

        String executeMethod = arguments[0].getClass().getSimpleName();
        String remotePeer = (String)objInst.getSkyWalkingDynamicField();
        AbstractSpan span = ContextManager.createExitSpan(MONGO_DB_OP_PREFIX + executeMethod, new ContextCarrier(), remotePeer);
        span.setComponent(ComponentsDefine.MONGO_DRIVER);
        Tags.DB_TYPE.set(span, DB_TYPE);
        SpanLayer.asDB(span);

        if (Config.Plugin.MongoDB.TRACE_PARAM) {
            Tags.DB_STATEMENT.set(span, executeMethod + " " + this.getTraceParam(arguments[0]));
        }

    }

    @Override public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Object ret) throws Throwable {
        ContextManager.stopSpan();
        return ret;
    }

    @Override public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {
        AbstractSpan activeSpan = ContextManager.activeSpan();
        activeSpan.errorOccurred();
        activeSpan.log(t);
    }

    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        Cluster cluster = (Cluster)allArguments[0];
        StringBuilder peers = new StringBuilder();
        for (ServerDescription description : cluster.getDescription().getServerDescriptions()) {
            ServerAddress address = description.getAddress();
            peers.append(address.getHost() + ":" + address.getPort() + ";");
        }

        objInst.setSkyWalkingDynamicField(peers.subSequence(0, peers.length() - 1).toString());
    }
}
