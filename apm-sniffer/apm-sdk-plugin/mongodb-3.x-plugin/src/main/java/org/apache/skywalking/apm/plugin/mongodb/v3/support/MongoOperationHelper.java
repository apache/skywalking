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

package org.apache.skywalking.apm.plugin.mongodb.v3.support;

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
import java.util.List;
import org.apache.skywalking.apm.plugin.mongodb.v3.MongoPluginConfig;
import org.bson.BsonDocument;

@SuppressWarnings({
    "deprecation",
    "Duplicates"
})
public class MongoOperationHelper {

    private MongoOperationHelper() {

    }

    /**
     * Convert ReadOperation interface or WriteOperation interface to the implementation class. Get the method name and
     * filter info.
     */
    @SuppressWarnings("rawtypes")
    public static String getTraceParam(Object obj) {
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
            BsonDocument filter = ((MapReduceWithInlineResultsOperation) obj).getFilter();
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
            return MongoConstants.EMPTY;
        }
    }

    private static String getFilter(List<? extends WriteRequest> writeRequestList) {
        StringBuilder params = new StringBuilder();
        for (WriteRequest request : writeRequestList) {
            if (request instanceof InsertRequest) {
                params.append(((InsertRequest) request).getDocument().toString()).append(",");
            } else if (request instanceof DeleteRequest) {
                params.append(((DeleteRequest) request).getFilter()).append(",");
            } else if (request instanceof UpdateRequest) {
                params.append(((UpdateRequest) request).getFilter()).append(",");
            }
            final int filterLengthLimit = MongoPluginConfig.Plugin.MongoDB.FILTER_LENGTH_LIMIT;
            if (filterLengthLimit > 0 && params.length() > filterLengthLimit) {
                return params.substring(0, filterLengthLimit) + "...";
            }
        }
        return params.toString();
    }

    private static String limitFilter(String filter) {
        final StringBuilder params = new StringBuilder();
        final int filterLengthLimit = MongoPluginConfig.Plugin.MongoDB.FILTER_LENGTH_LIMIT;
        if (filterLengthLimit > 0 && filter.length() > filterLengthLimit) {
            return params.append(filter, 0, filterLengthLimit).append("...").toString();
        } else {
            return filter;
        }
    }

}
