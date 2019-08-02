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
package org.apache.skywalking.apm.plugin.play.v2x;

import akka.stream.Materializer;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import play.api.routing.HandlerDef;
import play.mvc.Filter;
import play.mvc.Http;
import play.mvc.Result;
import play.routing.Router;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * @author AI
 * 2019-08-01
 */
@Singleton
public class TracingFilter extends Filter {

    @Inject
    public TracingFilter(Materializer mat) {
        super(mat);
    }

    @Override
    public CompletionStage<Result> apply(Function<Http.RequestHeader, CompletionStage<Result>> next, Http.RequestHeader request) {
        try {
            HandlerDef def = request.attrs().get(Router.Attrs.HANDLER_DEF);
            ContextCarrier carrier = new ContextCarrier();
            CarrierItem items = carrier.items();
            while (items.hasNext()) {
                items = items.next();
                Optional<String> value = request.getHeaders().get(items.getHeadKey());
                if (value.isPresent()) {
                    items.setHeadValue(value.get());
                }
            }
            String operationName = def.path().replaceAll("<[^/]+>", "");
            final AbstractSpan span = ContextManager.createEntrySpan(operationName, carrier);
            Tags.URL.set(span, request.path());
            Tags.HTTP.METHOD.set(span, request.method());
            span.setComponent(ComponentsDefine.PLAY);
            SpanLayer.asHttp(span);
            span.prepareForAsync();
            ContextManager.stopSpan(span);
            return next.apply(request).thenApply(result -> {
                span.asyncFinish();
                return result;
            });
        } catch (Throwable t) {
            return next.apply(request);
        }
    }
}
