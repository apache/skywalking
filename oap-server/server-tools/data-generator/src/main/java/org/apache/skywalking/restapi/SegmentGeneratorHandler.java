/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.skywalking.restapi;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.common.util.EventLoopGroups;
import com.linecorp.armeria.server.annotation.Default;
import com.linecorp.armeria.server.annotation.Delete;
import com.linecorp.armeria.server.annotation.Get;
import com.linecorp.armeria.server.annotation.Param;
import com.linecorp.armeria.server.annotation.Post;
import com.linecorp.armeria.server.annotation.ProducesJson;
import com.linecorp.armeria.server.annotation.RequestObject;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.source.Segment;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.Future;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SegmentGeneratorHandler {
    private final SourceReceiver sourceReceiver;
    private final Map<String, Future<?>> futures = new ConcurrentHashMap<>();
    private final EventLoopGroup eventLoopGroup = EventLoopGroups.newEventLoopGroup(10);

    public SegmentGeneratorHandler(ModuleManager manager) {
        sourceReceiver = manager.find(CoreModule.NAME).provider().getService(SourceReceiver.class);
    }

    @Post("/mock-data/segments/tasks")
    public HttpResponse generateMockSegments(
        @Default("0") @Param("size") int size,
        @Default("0") @Param("qps") int qps,
        @RequestObject SegmentRequest request) {

        if (size > 0 && qps > 0) {
            return HttpResponse.of(
                HttpStatus.BAD_REQUEST,
                MediaType.PLAIN_TEXT,
                "size and qps can't be both set");
        }
        log.info("Generate {} mock segments, qps: {}, template: {}", size, qps, request);

        final IntConsumer generator = unused -> {
            final Segment segment = request.next();
            log.debug("Generating segment: {}", segment);
            sourceReceiver.receive(segment);
        };
        final String requestId = UUID.randomUUID().toString();
        final Future<?> future;
        if (size > 0) {
            future = eventLoopGroup.submit(() -> IntStream
                .range(0, size)
                .forEach(generator));
        } else {
            future = eventLoopGroup.scheduleAtFixedRate(() -> IntStream
                .range(0, qps)
                .forEach(generator), 0, 1, TimeUnit.SECONDS);
        }

        futures.put(requestId, future);

        future.addListener(f -> {
            if (f.isDone()) {
                futures.remove(requestId);
                log.info("Generate mock segments finished: {}, requestId: {}", f.isSuccess(),
                    requestId);
            }
            if (f.cause() != null && !(f.cause() instanceof CancellationException)) {
                log.error("Exception in future: ", f.cause());
            }
        });

        return HttpResponse.of(MediaType.PLAIN_TEXT, requestId);
    }

    @Delete("/mock-data/segments/task")
    public HttpResponse cancelRequest(@Param("requestId") String requestId) {
        final Future<?> future = futures.get(requestId);
        if (future == null) {
            return HttpResponse.of(
                HttpStatus.NOT_FOUND,
                MediaType.PLAIN_TEXT_UTF_8,
                "No such request: %s", requestId);
        }
        log.info("Cancelling request: {}", requestId);
        future.cancel(true);
        return HttpResponse.of(HttpStatus.OK);
    }

    @Delete("/mock-data/segments/tasks")
    public HttpResponse cancelAllRequests() {
        futures.forEach((t, u) -> {
            log.info("Cancelling request: {}", t);
            u.cancel(true);
        });
        return HttpResponse.of(HttpStatus.OK);
    }

    @ProducesJson
    @Get("/mock-data/segments/tasks")
    public Set<String> listRequest() {
        return futures.keySet();
    }
}
