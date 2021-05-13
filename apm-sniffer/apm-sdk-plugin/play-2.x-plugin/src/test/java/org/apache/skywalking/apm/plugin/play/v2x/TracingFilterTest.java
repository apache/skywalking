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
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.helper.SpanHelper;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.test.tools.SpanAssert;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
import play.api.http.MediaRange;
import play.api.mvc.RequestHeader;
import play.api.routing.HandlerDef;
import play.i18n.Lang;
import play.libs.typedmap.TypedKey;
import play.libs.typedmap.TypedMap;
import play.mvc.Http;
import play.mvc.Result;
import play.routing.Router;

import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import static org.apache.skywalking.apm.agent.test.tools.SpanAssert.assertComponent;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static play.mvc.Results.badRequest;
import static play.mvc.Results.ok;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class TracingFilterTest {

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;
    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    @Mock
    private Materializer materializer;

    private Http.RequestHeader request = new Http.RequestHeader() {
        @Override
        public String uri() {
            return "/projects/1";
        }

        @Override
        public String method() {
            return "GET";
        }

        @Override
        public String version() {
            return null;
        }

        @Override
        public String remoteAddress() {
            return null;
        }

        @Override
        public boolean secure() {
            return false;
        }

        @Override
        public TypedMap attrs() {
            HandlerDef def = new HandlerDef(null, null, null, "GET", null, null, "/projects/$projectId<[^/]+>", null, null);
            return TypedMap.empty().put(Router.Attrs.HANDLER_DEF, def);
        }

        @Override
        public Http.RequestHeader withAttrs(TypedMap typedMap) {
            return null;
        }

        @Override
        public <A> Http.RequestHeader addAttr(TypedKey<A> typedKey, A a) {
            return null;
        }

        @Override
        public Http.RequestHeader removeAttr(TypedKey<?> typedKey) {
            return null;
        }

        @Override
        public Http.Request withBody(Http.RequestBody requestBody) {
            return null;
        }

        @Override
        public String host() {
            return "localhost:8080";
        }

        @Override
        public String path() {
            return "/projects/1";
        }

        @Override
        public List<Lang> acceptLanguages() {
            return null;
        }

        @Override
        public List<MediaRange> acceptedTypes() {
            return null;
        }

        @Override
        public boolean accepts(String s) {
            return false;
        }

        @Override
        public Map<String, String[]> queryString() {
            return null;
        }

        @Override
        public String getQueryString(String s) {
            return null;
        }

        @Override
        public Http.Cookies cookies() {
            return null;
        }

        @Override
        public Http.Cookie cookie(String s) {
            return null;
        }

        @Override
        public Http.Headers getHeaders() {
            return new Http.Headers(new HashMap<>());
        }

        @Override
        public boolean hasBody() {
            return false;
        }

        @Override
        public Optional<String> contentType() {
            return Optional.empty();
        }

        @Override
        public Optional<String> charset() {
            return Optional.empty();
        }

        @Override
        public Optional<List<X509Certificate>> clientCertificateChain() {
            return Optional.empty();
        }

        @Override
        public RequestHeader asScala() {
            return null;
        }
    };

    @Test
    public void testStatusCodeIsOk() throws Exception {
        TracingFilter filter = new TracingFilter(materializer);
        Function<Http.RequestHeader, CompletionStage<Result>> next = requestHeader -> CompletableFuture.supplyAsync(() -> ok("Hello"));
        CompletionStage<Result> result = filter.apply(next, request);
        result.toCompletableFuture().get();
        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertHttpSpan(spans.get(0));
    }

    @Test
    public void testStatusCodeIsNotOk() throws Exception {
        TracingFilter filter = new TracingFilter(materializer);
        Function<Http.RequestHeader, CompletionStage<Result>> next = requestHeader -> CompletableFuture.supplyAsync(() -> badRequest("Hello"));
        CompletionStage<Result> result = filter.apply(next, request);
        result.toCompletableFuture().get();
        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertHttpSpan(spans.get(0));
        assertThat(SpanHelper.getErrorOccurred(spans.get(0)), is(true));
    }

    private void assertHttpSpan(AbstractTracingSpan span) {
        assertThat(span.getOperationName(), is("/projects/{projectId}"));
        assertComponent(span, ComponentsDefine.PLAY);
        SpanAssert.assertTag(span, 0, "localhost:8080/projects/1");
        assertThat(span.isEntry(), is(true));
        SpanAssert.assertLayer(span, SpanLayer.HTTP);
    }

}
