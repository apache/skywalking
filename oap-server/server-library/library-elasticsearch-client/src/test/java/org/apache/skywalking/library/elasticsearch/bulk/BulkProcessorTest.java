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
 */

package org.apache.skywalking.library.elasticsearch.bulk;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.apache.skywalking.library.elasticsearch.requests.factory.v7plus.codec.V78Codec;
import org.apache.skywalking.library.elasticsearch.response.bulk.BulkItemResult;
import org.apache.skywalking.library.elasticsearch.response.bulk.BulkResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BulkProcessorTest {
    /**
     * A response reported in https://github.com/apache/skywalking/issues/14113: Elasticsearch returns overall
     * HTTP 200 while an item is rejected because the index is blocked by the flood-stage disk watermark.
     */
    private static final String BLOCKED_ITEM_RESPONSE = "{"
        + "\"took\":0,"
        + "\"errors\":true,"
        + "\"items\":[{"
        + "  \"update\":{"
        + "    \"_index\":\"blocked-test-index\","
        + "    \"_id\":\"oap-block-check-1\","
        + "    \"status\":429,"
        + "    \"error\":{"
        + "      \"type\":\"cluster_block_exception\","
        + "      \"reason\":\"index [blocked-test-index] blocked by: [TOO_MANY_REQUESTS/12/disk usage exceeded flood-stage watermark, index has read-only-allow-delete block];\""
        + "    }"
        + "  }"
        + "}]}";

    private static final String ALL_SUCCEEDED_RESPONSE = "{"
        + "\"took\":1,"
        + "\"errors\":false,"
        + "\"items\":[{\"index\":{\"_index\":\"test-index\",\"_id\":\"1\",\"status\":201}}]}";

    @Test
    void decodesItemLevelFailureFromHttp200Response() throws Exception {
        final BulkResponse response = decode(BLOCKED_ITEM_RESPONSE);

        assertTrue(response.isErrors());
        assertEquals(1, response.getItems().size());
        final BulkItemResult item = response.getItems().get(0).values().iterator().next();
        assertEquals(429, item.getStatus());
        assertEquals("cluster_block_exception", item.getError().getType());
    }

    @Test
    void decodesAllSucceededResponse() throws Exception {
        final BulkResponse response = decode(ALL_SUCCEEDED_RESPONSE);

        assertFalse(response.isErrors());
        assertNull(response.getItems().get(0).values().iterator().next().getError());
    }

    @Test
    void completesAllHoldersWhenNoErrors() throws Exception {
        final BulkResponse response = decode(ALL_SUCCEEDED_RESPONSE);
        final CompletableFuture<Void> future = new CompletableFuture<>();
        final List<BulkProcessor.Holder> holders = holdersOf(future);

        BulkProcessor.completeHolders(holders, response);

        assertTrue(future.isDone());
        assertFalse(future.isCompletedExceptionally());
    }

    @Test
    void failsOnlyTheItemsThatAreRejectedByElasticsearchWithASummaryMessage() throws Exception {
        final String twoItemResponse = "{"
            + "\"errors\":true,"
            + "\"items\":["
            + "  {\"index\":{\"_index\":\"idx\",\"_id\":\"1\",\"status\":201}},"
            + "  {\"update\":{\"_index\":\"idx\",\"_id\":\"rejected-doc-2\",\"status\":429,"
            + "    \"error\":{\"type\":\"cluster_block_exception\",\"reason\":\"disk usage exceeded flood-stage watermark\"}}}"
            + "]}";
        final BulkResponse response = decode(twoItemResponse);

        final CompletableFuture<Void> succeeded = new CompletableFuture<>();
        final CompletableFuture<Void> failed = new CompletableFuture<>();
        final List<BulkProcessor.Holder> holders = Arrays.asList(
            new BulkProcessor.Holder(succeeded, "req-1"),
            new BulkProcessor.Holder(failed, "req-2"));

        BulkProcessor.completeHolders(holders, response);

        assertTrue(succeeded.isDone());
        assertFalse(succeeded.isCompletedExceptionally());

        assertTrue(failed.isDone());
        assertTrue(failed.isCompletedExceptionally());
        final ExecutionException ex = assertThrows(ExecutionException.class, failed::get);
        final String message = ex.getCause().getMessage();
        assertTrue(message.contains("1 of 2 items rejected"), message);
        assertTrue(message.contains("code=429 type=cluster_block_exception count=1"), message);
        assertFalse(message.contains("rejected-doc-2"), "the rejected document id must not be logged: " + message);
        assertFalse(message.contains("flood-stage"), "the raw ES error reason must not be logged: " + message);
    }

    @Test
    void failsRequestsThatHaveNoCorrespondingResponseItem() throws Exception {
        // "errors: true" but only one item reported for two requests in the chunk.
        final BulkResponse response = decode(BLOCKED_ITEM_RESPONSE);

        final CompletableFuture<Void> withItem = new CompletableFuture<>();
        final CompletableFuture<Void> withoutItem = new CompletableFuture<>();
        final List<BulkProcessor.Holder> holders = Arrays.asList(
            new BulkProcessor.Holder(withItem, "req-1"),
            new BulkProcessor.Holder(withoutItem, "req-2"));

        BulkProcessor.completeHolders(holders, response);

        assertTrue(withItem.isCompletedExceptionally());
        assertTrue(withoutItem.isCompletedExceptionally());
        final String message = assertThrows(ExecutionException.class, withoutItem::get).getCause().getMessage();
        assertTrue(message.contains("no response item count=1"), message);
    }

    @Test
    void itemResultOfReturnsNullWhenIndexOutOfBounds() {
        assertNull(BulkProcessor.itemResultOf(null, 0));
        assertNull(BulkProcessor.itemResultOf(new ArrayList<>(), 0));
    }

    private static BulkResponse decode(final String json) throws Exception {
        try (final InputStream is = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))) {
            return V78Codec.INSTANCE.decode(is, BulkResponse.class);
        }
    }

    private static List<BulkProcessor.Holder> holdersOf(final CompletableFuture<Void> future) {
        final List<BulkProcessor.Holder> holders = new ArrayList<>();
        holders.add(new BulkProcessor.Holder(future, "req"));
        return holders;
    }
}
