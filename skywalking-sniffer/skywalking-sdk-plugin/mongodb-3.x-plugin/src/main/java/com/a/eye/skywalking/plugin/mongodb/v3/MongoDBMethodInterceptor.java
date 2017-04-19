package com.a.eye.skywalking.plugin.mongodb.v3;

import java.util.List;

import org.bson.BsonDocument;

import com.a.eye.skywalking.api.context.ContextManager;
import com.a.eye.skywalking.api.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.MethodInterceptResult;
import com.a.eye.skywalking.trace.Span;
import com.a.eye.skywalking.trace.tag.Tags;
import com.mongodb.ReadPreference;
import com.mongodb.bulk.DeleteRequest;
import com.mongodb.bulk.InsertRequest;
import com.mongodb.bulk.UpdateRequest;
import com.mongodb.bulk.WriteRequest;
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

/**
 * {@link MongoDBMethodInterceptor} intercept method of {@link com.mongodb.Mongo#execute(ReadOperation, ReadPreference)}
 * or {@link com.mongodb.Mongo#execute(WriteOperation)}. record the mongoDB host, operation name and the key of the
 * operation.
 *
 * @author baiyang
 */
public class MongoDBMethodInterceptor implements InstanceMethodsAroundInterceptor {

    /**
     * The key name that MongoDB host in {@link EnhancedClassInstanceContext#context}.
     */
    static final String MONGODB_HOST = "MONGODB_HOST";

    /**
     * The key name that MongoDB port in {@link EnhancedClassInstanceContext#context}.
     */
    static final String MONGODB_PORT = "MONGODB_PORT";

    private static final String MONGODB_COMPONENT = "MongoDB";

    private static final String METHOD = "MongoDB/";

    private static final int FILTER_LENGTH_LIMIT = 256;

    @Override
    public void beforeMethod(final EnhancedClassInstanceContext context,
            final InstanceMethodInvokeContext interceptorContext, final MethodInterceptResult result) {
        Object[] arguments = interceptorContext.allArguments();
        OperationInfo operationInfo = this.getReadOperationInfo(arguments[0]);
        Span span = ContextManager.createSpan(METHOD + operationInfo.getMethodName());
        Tags.COMPONENT.set(span, MONGODB_COMPONENT);
        Tags.DB_TYPE.set(span, MONGODB_COMPONENT);
        Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_CLIENT);
        Tags.SPAN_LAYER.asDB(span);
        Tags.DB_STATEMENT.set(span, operationInfo.getMethodName() + " " + operationInfo.getFilter());
    }

    @Override
    public Object afterMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
            Object ret) {
        Span span = ContextManager.activeSpan();
        Tags.PEER_HOST.set(span, context.get(MONGODB_HOST, String.class));
        Tags.PEER_PORT.set(span, (Integer) context.get(MONGODB_PORT));
        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(Throwable t, EnhancedClassInstanceContext context,
            InstanceMethodInvokeContext interceptorContext) {
        ContextManager.activeSpan().log(t);
    }

    /**
     * Convert ReadOperation interface or WriteOperation interface to the implementation class. Get the method name and
     * filter info.
     */
    @SuppressWarnings("rawtypes")
    private OperationInfo getReadOperationInfo(Object obj) {
        if (obj instanceof CountOperation) {
            BsonDocument filter = ((CountOperation) obj).getFilter();
            return new OperationInfo(ReadMethod.COUNT.getName(), limitFilter(filter.toString()));
        } else if (obj instanceof DistinctOperation) {
            BsonDocument filter = ((DistinctOperation) obj).getFilter();
            return new OperationInfo(ReadMethod.DISTINCT.getName(), limitFilter(filter.toString()));
        } else if (obj instanceof FindOperation) {
            BsonDocument filter = ((FindOperation) obj).getFilter();
            return new OperationInfo(ReadMethod.FIND.getName(), limitFilter(filter.toString()));
        } else if (obj instanceof GroupOperation) {
            BsonDocument filter = ((GroupOperation) obj).getFilter();
            return new OperationInfo(ReadMethod.GROUP.getName(), limitFilter(filter.toString()));
        } else if (obj instanceof ListCollectionsOperation) {
            BsonDocument filter = ((ListCollectionsOperation) obj).getFilter();
            return new OperationInfo(ReadMethod.LIST_COLLECTIONS.getName(), limitFilter(filter.toString()));
        } else if (obj instanceof MapReduceWithInlineResultsOperation) {
            BsonDocument filter = ((ListCollectionsOperation) obj).getFilter();
            return new OperationInfo(ReadMethod.MAPREDUCE_WITHINLINE_RESULTS.getName(), limitFilter(filter.toString()));
        } else if (obj instanceof DeleteOperation) {
            List<DeleteRequest> filter = ((DeleteOperation) obj).getDeleteRequests();
            return new OperationInfo(WriteMethod.DELETE.getName(), limitFilter(filter.toString()));
        } else if (obj instanceof InsertOperation) {
            List<InsertRequest> filter = ((InsertOperation) obj).getInsertRequests();
            return new OperationInfo(WriteMethod.INSERT.getName(), limitFilter(filter.toString()));
        } else if (obj instanceof UpdateOperation) {
            List<UpdateRequest> filter = ((UpdateOperation) obj).getUpdateRequests();
            return new OperationInfo(WriteMethod.UPDATE.getName(), limitFilter(filter.toString()));
        } else if (obj instanceof CreateCollectionOperation) {
            String filter = ((CreateCollectionOperation) obj).getCollectionName();
            return new OperationInfo(WriteMethod.CREATECOLLECTION.getName(), limitFilter(filter));
        } else if (obj instanceof CreateIndexesOperation) {
            List<String> filter = ((CreateIndexesOperation) obj).getIndexNames();
            return new OperationInfo(WriteMethod.CREATEINDEXES.getName(), limitFilter(filter.toString()));
        } else if (obj instanceof CreateViewOperation) {
            String filter = ((CreateViewOperation) obj).getViewName();
            return new OperationInfo(WriteMethod.CREATEVIEW.getName(), limitFilter(filter));
        } else if (obj instanceof FindAndDeleteOperation) {
            BsonDocument filter = ((FindAndDeleteOperation) obj).getFilter();
            return new OperationInfo(WriteMethod.FINDANDDELETE.getName(), limitFilter(filter.toString()));
        } else if (obj instanceof FindAndReplaceOperation) {
            BsonDocument filter = ((FindAndReplaceOperation) obj).getFilter();
            return new OperationInfo(WriteMethod.FINDANDREPLACE.getName(), limitFilter(filter.toString()));
        } else if (obj instanceof FindAndUpdateOperation) {
            BsonDocument filter = ((FindAndUpdateOperation) obj).getFilter();
            return new OperationInfo(WriteMethod.FINDANDUPDATE.getName(), limitFilter(filter.toString()));
        } else if (obj instanceof MapReduceToCollectionOperation) {
            BsonDocument filter = ((MapReduceToCollectionOperation) obj).getFilter();
            return new OperationInfo(WriteMethod.MAPREDUCETOCOLLECTION.getName(), limitFilter(filter.toString()));
        } else if (obj instanceof MixedBulkWriteOperation) {
            List<? extends WriteRequest> list = ((MixedBulkWriteOperation) obj).getWriteRequests();
            StringBuilder params = new StringBuilder();
            for (WriteRequest request : list) {
                if (request instanceof InsertRequest) {
                    params.append(((InsertRequest) request).getDocument().toString()).append(",");
                } else if (request instanceof DeleteRequest) {
                    params.append(((DeleteRequest) request).getFilter()).append(",");
                } else if (request instanceof UpdateRequest) {
                    params.append(((UpdateRequest) request).getFilter()).append(",");
                }
                if (params.length() > FILTER_LENGTH_LIMIT) {
                    params.append("...");
                    break;
                }
            }
            return new OperationInfo(WriteMethod.MIXEDBULKWRITE.getName(), params.toString());
        } else {
            return new OperationInfo(obj.getClass().getSimpleName());
        }
    }

    private String limitFilter(String filter) {
        final StringBuilder params = new StringBuilder();
        if (filter.length() > FILTER_LENGTH_LIMIT) {
            return params.append(filter.substring(0, FILTER_LENGTH_LIMIT)).append("...").toString();
        } else {
            return filter;
        }
    }

}
