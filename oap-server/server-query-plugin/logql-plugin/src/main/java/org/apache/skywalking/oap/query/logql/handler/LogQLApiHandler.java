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

package org.apache.skywalking.oap.query.logql.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.common.ResponseHeaders;
import com.linecorp.armeria.server.annotation.Get;
import com.linecorp.armeria.server.annotation.Param;
import com.linecorp.armeria.server.annotation.Path;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.ParseTree;
import org.apache.skywalking.logql.rt.grammar.LogQLLexer;
import org.apache.skywalking.logql.rt.grammar.LogQLParser;
import org.apache.skywalking.oap.query.logql.entity.LabelName;
import org.apache.skywalking.oap.query.logql.entity.LogDirection;
import org.apache.skywalking.oap.query.logql.entity.ResultStatus;
import org.apache.skywalking.oap.query.logql.entity.response.LabelValuesQueryRsp;
import org.apache.skywalking.oap.query.logql.entity.response.LabelsQueryRsp;
import org.apache.skywalking.oap.query.logql.entity.response.LogRangeQueryRsp;
import org.apache.skywalking.oap.query.logql.entity.response.QueryResponse;
import org.apache.skywalking.oap.query.logql.entity.response.ResultType;
import org.apache.skywalking.oap.query.logql.entity.response.StreamLog;
import org.apache.skywalking.oap.query.logql.entity.response.TimeValuePair;
import org.apache.skywalking.oap.query.logql.rt.LogQLExprVisitor;
import org.apache.skywalking.oap.query.logql.rt.exception.ParseErrorListener;
import org.apache.skywalking.oap.query.logql.rt.result.LogQLParseResult;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagType;
import org.apache.skywalking.oap.server.core.query.DurationUtils;
import org.apache.skywalking.oap.server.core.query.LogQueryService;
import org.apache.skywalking.oap.server.core.query.TagAutoCompleteQueryService;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.TraceScopeCondition;
import org.apache.skywalking.oap.server.core.query.type.Log;
import org.apache.skywalking.oap.server.core.query.type.Logs;
import org.apache.skywalking.oap.server.core.query.type.Pagination;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.StringUtil;

public class LogQLApiHandler {

    private final LogQueryService logQueryService;
    private final TagAutoCompleteQueryService tagAutoCompleteQueryService;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public LogQLApiHandler(ModuleManager moduleManager) {
        this.logQueryService = moduleManager.find(CoreModule.NAME)
                                            .provider()
                                            .getService(LogQueryService.class);
        this.tagAutoCompleteQueryService = moduleManager.find(CoreModule.NAME)
                                                        .provider()
                                                        .getService(TagAutoCompleteQueryService.class);
    }

    @Get
    @Path("/loki/api/v1/labels")
    public HttpResponse labels(
        @Param("start") Long start,
        @Param("end") Long end) throws IOException {
        LabelsQueryRsp labelsQueryRsp = new LabelsQueryRsp();
        labelsQueryRsp.setStatus(ResultStatus.SUCCESS);

        Duration duration = DurationUtils.timestamp2Duration(nano2Millis(start), nano2Millis(end));
        tagAutoCompleteQueryService.queryTagAutocompleteKeys(TagType.LOG, duration)
                                   .forEach(tag -> labelsQueryRsp.getData().add(tag));

        return successResponse(labelsQueryRsp);
    }

    @Get
    @Path("/loki/api/v1/label/{label_name}/values")
    public HttpResponse labelValues(
        @Param("label_name") String labelName,
        @Param("start") Long start,
        @Param("end") Long end) throws IOException {
        LabelValuesQueryRsp response = new LabelValuesQueryRsp();
        response.setStatus(ResultStatus.SUCCESS);

        Duration duration = DurationUtils.timestamp2Duration(nano2Millis(start), nano2Millis(end));
        tagAutoCompleteQueryService.queryTagAutocompleteValues(TagType.LOG, labelName, duration)
                                   .forEach(value -> response.getData().add(value));

        return successResponse(response);
    }

    @Get
    @Path("/loki/api/v1/query_range")
    public HttpResponse rangeQuery(
        @Param("start") Long start,
        @Param("end") Long end,
        @Param("query") String query,
        @Param("limit") Integer limit,
        @Param("direction") LogDirection direction) throws IOException {
        LogRangeQueryRsp logRangeQueryRsp = new LogRangeQueryRsp();
        logRangeQueryRsp.setStatus(ResultStatus.SUCCESS);

        LogQLLexer lexer = new LogQLLexer(CharStreams.fromString(query));
        lexer.addErrorListener(new ParseErrorListener());
        LogQLParser parser = new LogQLParser(new CommonTokenStream(lexer));
        parser.addErrorListener(new ParseErrorListener());
        ParseTree tree;
        try {
            tree = parser.root();
        } catch (ParseCancellationException e) {
            return badResponse(e.getMessage());
        }

        LogQLExprVisitor visitor = new LogQLExprVisitor();
        LogQLParseResult parseResult = visitor.visit(tree);
        Map<String, String> labelMap = parseResult.getLabelMap();

        String serviceId = labelMap.containsKey(LabelName.SERVICE.getLabel()) ?
            IDManager.ServiceID.buildId(labelMap.get(LabelName.SERVICE.getLabel()), true) : null;
        String serviceInstanceId = labelMap.containsKey(LabelName.SERVICE_INSTANCE.getLabel()) ?
            IDManager.ServiceInstanceID.buildId(serviceId, labelMap.get(LabelName.SERVICE_INSTANCE.getLabel())) : null;
        String endpointId = labelMap.containsKey(LabelName.ENDPOINT.getLabel()) ?
            IDManager.EndpointID.buildId(serviceId, labelMap.get(LabelName.ENDPOINT.getLabel())) : null;

        String traceId = labelMap.get(LabelName.TRACE_ID.getLabel());
        TraceScopeCondition traceScopeCondition = new TraceScopeCondition();
        if (StringUtil.isNotEmpty(traceId)) {
            traceScopeCondition.setTraceId(traceId);
        }

        List<Tag> tags = labelMap.entrySet().stream()
                                 // labels in stream selector all belongs to log tag except labels define in LabelName
                                 .filter(entry -> !LabelName.containsLabel(entry.getKey()))
                                 .map(entry -> new Tag(entry.getKey(), entry.getValue()))
                                 .collect(Collectors.toList());

        Duration duration = DurationUtils.timestamp2Duration(nano2Millis(start), nano2Millis(end));

        Logs logs = logQueryService.queryLogs(
            serviceId,
            serviceInstanceId,
            endpointId,
            traceScopeCondition,
            new Pagination(1, limit),
            direction.getOrder(),
            duration,
            tags,
            parseResult.getKeywordsOfContent(),
            parseResult.getExcludingKeywordsOfContent()
        );

        if (StringUtil.isNotEmpty(logs.getErrorReason())) {
            return badResponse(logs.getErrorReason());
        }

        final StreamLog responseData = new StreamLog();
        responseData.setResultType(ResultType.STREAMS);
        logRangeQueryRsp.setData(responseData);

        logs.getLogs().stream()
            .collect(
                Collectors.groupingBy(
                    log -> log.getServiceId() + log.getServiceInstanceId() + log.getEndpointName() + log.getTraceId()))
            .forEach((streamKey, logList) -> {
                StreamLog.Result result = new StreamLog.Result();

                Map<String, String> labels = new HashMap<>();
                labels.put(LabelName.SERVICE.getLabel(), logList.get(0).getServiceName());
                labels.put(LabelName.SERVICE_INSTANCE.getLabel(), logList.get(0).getServiceInstanceName());
                labels.put(LabelName.ENDPOINT.getLabel(), logList.get(0).getEndpointName());
                labels.put(LabelName.TRACE_ID.getLabel(), logList.get(0).getTraceId());
                result.setStream(labels);

                List<TimeValuePair> timeValuePairs = new ArrayList<>();
                for (final Log log : logList) {
                    timeValuePairs.add(new TimeValuePair(
                        String.valueOf(millis2Nano(log.getTimestamp())),
                        log.getContent()
                    ));
                }
                result.setValues(timeValuePairs);

                responseData.getResult().add(result);
            });

        return successResponse(logRangeQueryRsp);
    }

    private long nano2Millis(Long nanosecond) {
        return nanosecond / 1000000;
    }

    private long millis2Nano(Long nanosecond) {
        return nanosecond * 1000000;
    }

    private HttpResponse badResponse(String message) {
        return HttpResponse.of(ResponseHeaders.builder(HttpStatus.BAD_REQUEST)
                                              .contentType(MediaType.PLAIN_TEXT_UTF_8)
                                              .build(), HttpData.ofUtf8(message));
    }

    private HttpResponse successResponse(QueryResponse response) throws JsonProcessingException {
        return HttpResponse.of(ResponseHeaders.builder(HttpStatus.OK)
                                              .contentType(MediaType.JSON_UTF_8)
                                              .build(), HttpData.ofUtf8(MAPPER.writeValueAsString(response)));
    }
}
