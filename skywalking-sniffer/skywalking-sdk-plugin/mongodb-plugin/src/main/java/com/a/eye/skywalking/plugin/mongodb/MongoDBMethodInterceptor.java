package com.a.eye.skywalking.plugin.mongodb;

import java.util.List;

import com.a.eye.skywalking.api.context.ContextManager;
import com.a.eye.skywalking.api.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.MethodInterceptResult;
import com.a.eye.skywalking.trace.Span;
import com.a.eye.skywalking.trace.tag.Tags;
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
import com.mongodb.operation.UpdateOperation;

/**
 * {@link MongoDBMethodInterceptor} intercept method of {@link com.mongodb.Mongo#execute(ReadOperation, ReadPreference)}
 * or {@link com.mongodb.Mongo#execute(WriteOperation)}. record the mongoDB host, operation name and the key of the operation.
 *
 * @author baiyang
 */
public class MongoDBMethodInterceptor implements InstanceMethodsAroundInterceptor {

    /**
     * The key name that MongoDB host in {@link EnhancedClassInstanceContext#context}.
     */
    protected static final String MONGODB_HOST = "MONGODB_HOST";

    /**
     * The key name that MongoDB port in {@link EnhancedClassInstanceContext#context}.
     */
    protected static final String MONGODB_PORT = "MONGODB_PORT";

    private static final String MONGODB_COMPONENT = "MongoDB";
    
    @Override
    public void beforeMethod(final EnhancedClassInstanceContext context,
            final InstanceMethodInvokeContext interceptorContext, final MethodInterceptResult result) {
        Object[] arguments = interceptorContext.allArguments();
        OperationInfo operationInfo = this.getReadOperationInfo(arguments[0]);
        Span span = ContextManager.createSpan("MongoDB/" + operationInfo.getMethodName());
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
     *  Convert ReadOperation interface or WriteOperation interface to the implementation class.
     *  Get the method name and filter info.
     */
    @SuppressWarnings("rawtypes")
    private OperationInfo getReadOperationInfo(Object obj) {
        if (obj instanceof CountOperation) {
            return new OperationInfo(ReadMethod.COUNT.getName(), ((CountOperation) obj).getFilter().toString());
        } else if (obj instanceof DistinctOperation) {
            return new OperationInfo(ReadMethod.DISTINCT.getName(), ((DistinctOperation) obj).getFilter().toString());
        } else if (obj instanceof FindOperation) {
            return new OperationInfo(ReadMethod.FIND.getName(), ((FindOperation) obj).getFilter().toString());
        } else if (obj instanceof GroupOperation) {
            return new OperationInfo(ReadMethod.GROUP.getName(), ((GroupOperation) obj).getFilter().toString());
        } else if (obj instanceof ListCollectionsOperation) {
            return new OperationInfo(ReadMethod.LIST_COLLECTIONS.getName(), ((ListCollectionsOperation) obj).getFilter().toString());
        } else if (obj instanceof MapReduceWithInlineResultsOperation) {
            return new OperationInfo(ReadMethod.MAPREDUCE_WITHINLINE_RESULTS.getName(), ((ListCollectionsOperation) obj).getFilter().toString());
        } else  if (obj instanceof DeleteOperation) {
            return new OperationInfo(WriteMethod.DELETE.getName(), ((DeleteOperation) obj).getDeleteRequests().toString());
        } else if (obj instanceof InsertOperation) {
            return new OperationInfo(WriteMethod.INSERT.getName(), ((InsertOperation) obj).getInsertRequests().toString());
        } else if (obj instanceof UpdateOperation) {
            return new OperationInfo(WriteMethod.UPDATE.getName(), ((UpdateOperation) obj).getUpdateRequests().toString());
        } else if (obj instanceof CreateCollectionOperation) {
            return new OperationInfo(WriteMethod.CREATECOLLECTION.getName(), ((CreateCollectionOperation) obj).getCollectionName());
        } else if (obj instanceof CreateIndexesOperation) {
            return new OperationInfo(WriteMethod.CREATEINDEXES.getName(), ((CreateIndexesOperation) obj).getIndexNames().toString());
        } else if (obj instanceof CreateViewOperation) {
            return new OperationInfo(WriteMethod.CREATEVIEW.getName(), ((CreateViewOperation) obj).getViewName());
        } else if (obj instanceof FindAndDeleteOperation) {
            return new OperationInfo(WriteMethod.FINDANDDELETE.getName(), ((FindAndDeleteOperation) obj).getFilter().toString());
        } else if (obj instanceof FindAndReplaceOperation) {
            return new OperationInfo(WriteMethod.FINDANDREPLACE.getName(), ((FindAndReplaceOperation) obj).getFilter().toString());
        } else if (obj instanceof FindAndUpdateOperation) {
            return new OperationInfo(WriteMethod.FINDANDUPDATE.getName(), ((FindAndUpdateOperation) obj).getFilter().toString());
        } else if (obj instanceof MapReduceToCollectionOperation) {
            return new OperationInfo(WriteMethod.MAPREDUCETOCOLLECTION.getName(), ((MapReduceToCollectionOperation) obj).getFilter().toString());
        } else if (obj instanceof MixedBulkWriteOperation) {
            List<? extends WriteRequest> list = ((MixedBulkWriteOperation) obj).getWriteRequests();
            StringBuilder sb = new StringBuilder();
            for (WriteRequest request : list) {
                if (request instanceof InsertRequest) {
                    sb.append(((InsertRequest) request).getDocument().toString()).append(",");
                } else if (request instanceof DeleteRequest) {
                    sb.append(((DeleteRequest) request).getFilter()).append(",");
                } else if (request instanceof UpdateRequest) {
                    sb.append(((UpdateRequest) request).getFilter()).append(",");
                }
            }
            return new OperationInfo(WriteMethod.MIXEDBULKWRITE.getName(), sb.toString());
        } else {
            return new OperationInfo(WriteMethod.UNKNOW.getName());
        }
    }

}
