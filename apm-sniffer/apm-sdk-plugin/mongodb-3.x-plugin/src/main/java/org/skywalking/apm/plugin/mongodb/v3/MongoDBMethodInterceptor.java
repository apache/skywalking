package org.skywalking.apm.plugin.mongodb.v3;

import com.mongodb.ReadPreference;
import com.mongodb.bulk.DeleteRequest;
import com.mongodb.bulk.InsertRequest;
import com.mongodb.bulk.UpdateRequest;
import com.mongodb.bulk.WriteRequest;
import com.mongodb.operation.*;
import org.bson.BsonDocument;
import org.skywalking.apm.agent.core.conf.Config;
import org.skywalking.apm.agent.core.context.ContextManager;
import org.skywalking.apm.agent.core.plugin.interceptor.EnhancedClassInstanceContext;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.skywalking.apm.agent.core.context.trace.Span;
import org.skywalking.apm.agent.core.context.tag.Tags;

import java.util.List;

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

    private static final String EMPTY = "";

    @Override
    public void beforeMethod(final EnhancedClassInstanceContext context,
                             final InstanceMethodInvokeContext interceptorContext, final MethodInterceptResult result) {
        Object[] arguments = interceptorContext.allArguments();

        String methodName = arguments[0].getClass().getSimpleName();
        Span span = ContextManager.createSpan(METHOD + methodName);
        Tags.COMPONENT.set(span, MONGODB_COMPONENT);
        Tags.DB_TYPE.set(span, MONGODB_COMPONENT);
        Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_CLIENT);
        Tags.SPAN_LAYER.asDB(span);

        if (Config.Plugin.MongoDB.TRACE_PARAM) {
            Tags.DB_STATEMENT.set(span, methodName + " " + this.getTraceParam(arguments[0]));
        }

    }

    @Override
    public Object afterMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
                              Object ret) {
        Span span = ContextManager.activeSpan();
        span.setPeerHost((String)context.get(MONGODB_HOST));
        span.setPort((Integer)context.get(MONGODB_PORT));
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
    private String getTraceParam(Object obj) {
        if (obj instanceof CountOperation) {
            BsonDocument filter = ((CountOperation) obj).getFilter();
            return limitFilter(filter.toString());
        } else if (obj instanceof DistinctOperation) {
            BsonDocument filter = ((DistinctOperation) obj).getFilter();
            return limitFilter(filter.toString());
        } else if (obj instanceof FindOperation) {
            BsonDocument filter = ((FindOperation) obj).getFilter();
            return limitFilter(filter.toString());
        } else if (obj instanceof GroupOperation) {
            BsonDocument filter = ((GroupOperation) obj).getFilter();
            return limitFilter(filter.toString());
        } else if (obj instanceof ListCollectionsOperation) {
            BsonDocument filter = ((ListCollectionsOperation) obj).getFilter();
            return limitFilter(filter.toString());
        } else if (obj instanceof MapReduceWithInlineResultsOperation) {
            BsonDocument filter = ((ListCollectionsOperation) obj).getFilter();
            return limitFilter(filter.toString());
        } else if (obj instanceof DeleteOperation) {
            List<DeleteRequest> writeRequestList = ((DeleteOperation) obj).getDeleteRequests();
            return getFilter(writeRequestList);
        } else if (obj instanceof InsertOperation) {
            List<InsertRequest> writeRequestList = ((InsertOperation) obj).getInsertRequests();
            return getFilter(writeRequestList);
        } else if (obj instanceof UpdateOperation) {
            List<UpdateRequest> writeRequestList = ((UpdateOperation) obj).getUpdateRequests();
            return getFilter(writeRequestList);
        } else if (obj instanceof CreateCollectionOperation) {
            String filter = ((CreateCollectionOperation) obj).getCollectionName();
            return limitFilter(filter);
        } else if (obj instanceof CreateIndexesOperation) {
            List<String> filter = ((CreateIndexesOperation) obj).getIndexNames();
            return limitFilter(filter.toString());
        } else if (obj instanceof CreateViewOperation) {
            String filter = ((CreateViewOperation) obj).getViewName();
            return limitFilter(filter);
        } else if (obj instanceof FindAndDeleteOperation) {
            BsonDocument filter = ((FindAndDeleteOperation) obj).getFilter();
            return limitFilter(filter.toString());
        } else if (obj instanceof FindAndReplaceOperation) {
            BsonDocument filter = ((FindAndReplaceOperation) obj).getFilter();
            return limitFilter(filter.toString());
        } else if (obj instanceof FindAndUpdateOperation) {
            BsonDocument filter = ((FindAndUpdateOperation) obj).getFilter();
            return limitFilter(filter.toString());
        } else if (obj instanceof MapReduceToCollectionOperation) {
            BsonDocument filter = ((MapReduceToCollectionOperation) obj).getFilter();
            return limitFilter(filter.toString());
        } else if (obj instanceof MixedBulkWriteOperation) {
            List<? extends WriteRequest> writeRequestList = ((MixedBulkWriteOperation) obj).getWriteRequests();
            return getFilter(writeRequestList);
        } else {
            return EMPTY;
        }
    }

    private String getFilter(List<? extends WriteRequest> writeRequestList) {
        StringBuilder params = new StringBuilder();
        for (WriteRequest request : writeRequestList) {
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

}
