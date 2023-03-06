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

package org.apache.skywalking.oap.query.promql.handler;

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
import com.linecorp.armeria.server.annotation.Post;
import graphql.org.antlr.v4.runtime.misc.ParseCancellationException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.apache.skywalking.oap.query.graphql.resolver.MetadataQueryV2;
import org.apache.skywalking.oap.query.graphql.resolver.MetricsQuery;
import org.apache.skywalking.oap.query.graphql.resolver.RecordsQuery;
import org.apache.skywalking.oap.query.promql.entity.ErrorType;
import org.apache.skywalking.oap.query.promql.entity.LabelName;
import org.apache.skywalking.oap.query.promql.entity.LabelValuePair;
import org.apache.skywalking.oap.query.promql.entity.MetricInstantData;
import org.apache.skywalking.oap.query.promql.entity.MetricMetadata;
import org.apache.skywalking.oap.query.promql.entity.MetricRangeData;
import org.apache.skywalking.oap.query.promql.entity.TimeValuePair;
import org.apache.skywalking.oap.query.promql.entity.response.ExprQueryRsp;
import org.apache.skywalking.oap.query.promql.entity.MetricInfo;
import org.apache.skywalking.oap.query.promql.entity.response.LabelValuesQueryRsp;
import org.apache.skywalking.oap.query.promql.entity.response.LabelsQueryRsp;
import org.apache.skywalking.oap.query.promql.entity.ResultStatus;
import org.apache.skywalking.oap.query.promql.entity.response.MetricRspData;
import org.apache.skywalking.oap.query.promql.entity.response.MetadataQueryRsp;
import org.apache.skywalking.oap.query.promql.entity.response.MetricType;
import org.apache.skywalking.oap.query.promql.entity.response.QueryResponse;
import org.apache.skywalking.oap.query.promql.entity.response.ResultType;
import org.apache.skywalking.oap.query.promql.entity.response.ScalarRspData;
import org.apache.skywalking.oap.query.promql.entity.response.SeriesQueryRsp;
import org.apache.skywalking.oap.query.promql.rt.PromOpUtils;
import org.apache.skywalking.oap.query.promql.rt.PromQLMatchVisitor;
import org.apache.skywalking.oap.query.promql.rt.exception.ParseErrorListener;
import org.apache.skywalking.oap.query.promql.rt.result.MatcherSetResult;
import org.apache.skywalking.oap.query.promql.rt.result.MetricsRangeResult;
import org.apache.skywalking.oap.query.promql.rt.PromQLExprQueryVisitor;
import org.apache.skywalking.oap.query.promql.rt.result.ParseResult;
import org.apache.skywalking.oap.query.promql.rt.result.ScalarResult;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.manual.endpoint.EndpointTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.service.ServiceTraffic;
import org.apache.skywalking.oap.server.core.query.MetricDefinition;
import org.apache.skywalking.oap.server.core.query.enumeration.Scope;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.Endpoint;
import org.apache.skywalking.oap.server.core.query.type.Service;
import org.apache.skywalking.oap.server.core.query.type.ServiceInstance;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnMetadata;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.promql.rt.grammar.PromQLLexer;
import org.apache.skywalking.promql.rt.grammar.PromQLParser;

import static org.apache.skywalking.oap.query.promql.rt.PromOpUtils.formatDoubleValue;
import static org.apache.skywalking.oap.query.promql.rt.PromOpUtils.timestamp2Duration;

public class PromQLApiHandler {
    private final MetadataQueryV2 metadataQuery;
    private final MetricsQuery metricsQuery;
    private final RecordsQuery recordsQuery;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public PromQLApiHandler(ModuleManager moduleManager) {
        this.metadataQuery = new MetadataQueryV2(moduleManager);
        this.metricsQuery = new MetricsQuery(moduleManager);
        this.recordsQuery = new RecordsQuery(moduleManager);
    }

    @Get
    @Path("/api/v1/metadata")
    public HttpResponse metadata(
        @Param("limit") Optional<Integer> limit,
        @Param("metric") Optional<String> metric) throws JsonProcessingException {
        MetadataQueryRsp response = new MetadataQueryRsp();
        response.setStatus(ResultStatus.SUCCESS);
        String regex = metric.orElse("");
        List<MetricDefinition> definitionList = metricsQuery.listMetrics(regex);
        int maxNum = limit.orElse(definitionList.size());
        for (int i = 0; i < maxNum; i++) {
            List<MetricMetadata> metadataList = new ArrayList<>();
            MetricMetadata metadata = new MetricMetadata(MetricType.GAUGE, "", "");
            metadataList.add(metadata);
            response.getData().put(definitionList.get(i).getName(), metadataList);
        }
        return jsonResponse(response);
    }

    /**
     * OAP doesn't support query label by start/end,
     * reserve these param to keep consistent with API protocol.
     */
    @Get
    @Post
    @Path("/api/v1/labels")
    public HttpResponse labels(
        @Param("match[]") Optional<String> match,
        @Param("start") Optional<String> start,
        @Param("end") Optional<String> end) throws IOException {
        LabelsQueryRsp response = new LabelsQueryRsp();
        if (match.isPresent()) {
            PromQLLexer lexer = new PromQLLexer(
                CharStreams.fromString(match.get()));
            lexer.addErrorListener(ParseErrorListener.INSTANCE);
            PromQLParser parser = new PromQLParser(new CommonTokenStream(lexer));
            parser.addErrorListener(ParseErrorListener.INSTANCE);
            ParseTree tree;
            try {
                tree = parser.expression();
            } catch (ParseCancellationException e) {
                response.setStatus(ResultStatus.ERROR);
                response.setErrorType(ErrorType.BAD_DATA);
                response.setError(e.getMessage());
                return jsonResponse(response);
            }
            PromQLMatchVisitor visitor = new PromQLMatchVisitor();
            MatcherSetResult parseResult = visitor.visit(tree);
            String metricName = parseResult.getMetricName();
            Optional<ValueColumnMetadata.ValueColumn> valueColumn = ValueColumnMetadata.INSTANCE.readValueColumnDefinition(
                metricName);
            if (valueColumn.isPresent()) {
                ValueColumnMetadata.ValueColumn metaData = valueColumn.get();
                Scope scope = Scope.Finder.valueOf(metaData.getScopeId());
                Column.ValueDataType dataType = metaData.getDataType();
                response.getData().addAll(buildLabelNames(scope, dataType));
            }
        } else {
            Arrays.stream(LabelName.values()).forEach(label -> {
                response.getData().add(label);
            });
        }
        response.setStatus(ResultStatus.SUCCESS);
        return jsonResponse(response);
    }

    /**
     * OAP doesn't support query label value by match/start/end,
     * reserve these param to keep consistent with API protocol.
     */
    @Get
    @Path("/api/v1/label/{label_name}/values")
    public HttpResponse labelValues(
        @Param("label_name") String labelName,
        @Param("match[]") Optional<String> match,
        @Param("start") Optional<String> start,
        @Param("end") Optional<String> end) throws IOException {
        LabelValuesQueryRsp response = new LabelValuesQueryRsp();
        response.setStatus(ResultStatus.SUCCESS);

        switch (LabelName.labelOf(labelName)) {
            case NAME:
                metricsQuery.listMetrics("").forEach(definition -> {
                    response.getData().add(definition.getName());
                });
                break;
            case LAYER:
                for (Layer layer : Layer.values()) {
                    response.getData().add(layer.name());
                }
                break;
            case SCOPE:
                for (Scope scope : Scope.values()) {
                    response.getData().add(scope.name());
                }
                break;
        }

        return jsonResponse(response);
    }

    @Get
    @Post
    @Path("/api/v1/series")
    public HttpResponse series(
        @Param("match[]") String match,
        @Param("start") String start,
        @Param("end") String end) throws IOException {
        long startTS = formatTimestamp2Millis(start);
        long endTS = formatTimestamp2Millis(end);
        Duration duration = timestamp2Duration(startTS, endTS);
        SeriesQueryRsp response = new SeriesQueryRsp();
        PromQLLexer lexer = new PromQLLexer(
            CharStreams.fromString(match));
        lexer.addErrorListener(ParseErrorListener.INSTANCE);
        PromQLParser parser = new PromQLParser(new CommonTokenStream(lexer));
        parser.addErrorListener(ParseErrorListener.INSTANCE);
        ParseTree tree;
        try {
            tree = parser.expression();
        } catch (ParseCancellationException e) {
            response.setStatus(ResultStatus.ERROR);
            response.setErrorType(ErrorType.BAD_DATA);
            response.setError(e.getMessage());
            return jsonResponse(response);
        }
        PromQLMatchVisitor visitor = new PromQLMatchVisitor();
        MatcherSetResult parseResult = visitor.visit(tree);
        String metricName = parseResult.getMetricName();

        Optional<ValueColumnMetadata.ValueColumn> valueColumn = ValueColumnMetadata.INSTANCE.readValueColumnDefinition(
            metricName);
        if (valueColumn.isPresent()) {
            ValueColumnMetadata.ValueColumn metaData = valueColumn.get();
            Scope scope = Scope.Finder.valueOf(metaData.getScopeId());
            Column.ValueDataType dataType = metaData.getDataType();
            response.getData().add(buildMetaMetricInfo(metricName, scope, dataType));
        } else if (Objects.equals(metricName, ServiceTraffic.INDEX_NAME)) {
            String serviceName = parseResult.getLabelMap().get(LabelName.SERVICE);
            if (StringUtil.isNotBlank(serviceName)) {
                Service service = metadataQuery.findService(serviceName);
                response.getData().add(buildMetricInfoFromTraffic(metricName, service));
            } else {
                List<Service> services = metadataQuery.listServices(
                    parseResult.getLabelMap().get(LabelName.LAYER));
                services.forEach(service -> {
                    response.getData().add(buildMetricInfoFromTraffic(metricName, service));
                });
            }
        } else if (Objects.equals(metricName, InstanceTraffic.INDEX_NAME)) {
            String serviceName = parseResult.getLabelMap().get(LabelName.SERVICE);
            String layer = parseResult.getLabelMap().get(LabelName.LAYER);
            List<ServiceInstance> instances = metadataQuery.listInstances(
                duration, IDManager.ServiceID.buildId(serviceName, Layer.valueOf(layer).isNormal()));
            instances.forEach(instance -> {
                response.getData().add(buildMetricInfoFromTraffic(metricName, instance));
            });
        } else if (Objects.equals(metricName, EndpointTraffic.INDEX_NAME)) {
            String serviceName = parseResult.getLabelMap().get(LabelName.SERVICE);
            String layer = parseResult.getLabelMap().get(LabelName.LAYER);
            String keyword = parseResult.getLabelMap().getOrDefault(LabelName.KEYWORD, "");
            String limit = parseResult.getLabelMap().getOrDefault(LabelName.LIMIT, "100");
            List<Endpoint> endpoints = metadataQuery.findEndpoint(
                keyword, IDManager.ServiceID.buildId(serviceName, Layer.valueOf(layer).isNormal()),
                Integer.parseInt(limit)
            );
            endpoints.forEach(endpoint -> {
                response.getData().add(buildMetricInfoFromTraffic(metricName, endpoint));
            });
        }

        response.setStatus(ResultStatus.SUCCESS);
        return jsonResponse(response);
    }

    @Get
    @Post
    @Path("/api/v1/query")
    public HttpResponse query(
        @Param("query") String query,
        @Param("time") Optional<String> time,
        @Param("timeout") Optional<String> timeout) throws IOException {
        long endTS = System.currentTimeMillis();
        if (time.isPresent()) {
            endTS = formatTimestamp2Millis(time.get());
        }
        long startTS = endTS - 120000; //look back 2m by default
        Duration duration = timestamp2Duration(startTS, endTS);
        ExprQueryRsp response = new ExprQueryRsp();

        PromQLLexer lexer = new PromQLLexer(
            CharStreams.fromString(query));
        lexer.addErrorListener(ParseErrorListener.INSTANCE);
        PromQLParser parser = new PromQLParser(new CommonTokenStream(lexer));
        parser.addErrorListener(ParseErrorListener.INSTANCE);
        ParseTree tree;
        try {
            tree = parser.expression();
        } catch (ParseCancellationException e) {
            response.setStatus(ResultStatus.ERROR);
            response.setErrorType(ErrorType.BAD_DATA);
            response.setError(e.getMessage());
            return jsonResponse(response);
        }
        PromQLExprQueryVisitor visitor = new PromQLExprQueryVisitor(
            metricsQuery, recordsQuery, duration, QueryType.INSTANT);
        ParseResult parseResult = visitor.visit(tree);

        if (parseResult == null) {
            response.setStatus(ResultStatus.ERROR);
            response.setErrorType(ErrorType.BAD_DATA);
            response.setError("Bad expression, can not parse it.");
        } else if (StringUtil.isBlank(parseResult.getErrorInfo())) {
            //The expression include range, such as: metric_name[1m]
            if (parseResult.isRangeExpression()) {
                buildMatrixRsp(parseResult, response);
            } else {
                switch (parseResult.getResultType()) {
                    case METRICS_RANGE:
                        buildVectorRsp(parseResult, response);
                        break;
                    case SCALAR:
                        buildScalarRsp(parseResult, response);
                        break;
                }
            }
        } else {
            response.setStatus(ResultStatus.ERROR);
            response.setErrorType(parseResult.getErrorType());
            response.setError(parseResult.getErrorInfo());
        }
        return jsonResponse(response);
    }

    /**
     * OAP adopt time step by start/end,
     * reserve step/timeout param to keep consistent with API protocol.
     */
    @Get
    @Post
    @Path("/api/v1/query_range")
    public HttpResponse query_range(
        @Param("query") String query,
        @Param("start") String start,
        @Param("end") String end,
        @Param("step") Optional<String> step,
        @Param("timeout") Optional<String> timeout) throws IOException {
        long startTS = formatTimestamp2Millis(start);
        long endTS = formatTimestamp2Millis(end);
        Duration duration = timestamp2Duration(startTS, endTS);
        ExprQueryRsp response = new ExprQueryRsp();
        PromQLLexer lexer = new PromQLLexer(
            CharStreams.fromString(query));
        lexer.addErrorListener(ParseErrorListener.INSTANCE);
        PromQLParser parser = new PromQLParser(new CommonTokenStream(lexer));
        parser.addErrorListener(ParseErrorListener.INSTANCE);
        ParseTree tree;
        try {
            tree = parser.expression();
        } catch (ParseCancellationException e) {
            response.setStatus(ResultStatus.ERROR);
            response.setErrorType(ErrorType.BAD_DATA);
            response.setError(e.getMessage());
            return jsonResponse(response);
        }

        PromQLExprQueryVisitor visitor = new PromQLExprQueryVisitor(
            metricsQuery, recordsQuery, duration, QueryType.RANGE);
        ParseResult parseResult = visitor.visit(tree);

        if (parseResult == null) {
            response.setStatus(ResultStatus.ERROR);
            response.setErrorType(ErrorType.BAD_DATA);
            response.setError("Bad expression, can not parse it.");
        } else if (StringUtil.isBlank(parseResult.getErrorInfo())) {
            switch (parseResult.getResultType()) {
                case METRICS_RANGE:
                    buildMatrixRsp(parseResult, response);
                    break;
                case SCALAR:
                    buildScalarMatrixRsp(duration, parseResult, response);
                    break;
            }
        } else {
            response.setStatus(ResultStatus.ERROR);
            response.setErrorType(parseResult.getErrorType());
            response.setError(parseResult.getErrorInfo());
        }
        return jsonResponse(response);
    }

    private HttpResponse jsonResponse(QueryResponse response) throws JsonProcessingException {
        return HttpResponse.of(ResponseHeaders.builder(HttpStatus.OK)
                                              .contentType(MediaType.JSON)
                                              .build(), HttpData.ofUtf8(MAPPER.writeValueAsString(response)));
    }

    private void buildVectorRsp(ParseResult parseResult, ExprQueryRsp response) {
        MetricRspData exprRspData = new MetricRspData();
        response.setData(exprRspData);
        exprRspData.setResultType(ResultType.VECTOR);
        MetricsRangeResult matrixResult = (MetricsRangeResult) parseResult;
        response.setStatus(ResultStatus.SUCCESS);
        matrixResult.getMetricDataList().forEach(rangData -> {
            List<TimeValuePair> values = rangData.getValues();
            if (values.size() > 0) {
                MetricInstantData instantData = new MetricInstantData();
                instantData.setValue(values.get(values.size() - 1));
                instantData.setMetric(rangData.getMetric());
                exprRspData.getResult().add(instantData);
            }
        });
    }

    private void buildScalarRsp(ParseResult parseResult, ExprQueryRsp response) {
        ScalarRspData scalarRspData = new ScalarRspData();
        response.setData(scalarRspData);
        scalarRspData.setResultType(ResultType.SCALAR);
        ScalarResult scalarResult = (ScalarResult) parseResult;
        response.setStatus(ResultStatus.SUCCESS);
        //need return int type
        scalarRspData.setResult(
            new TimeValuePair(
                System.currentTimeMillis() / 1000,
                formatDoubleValue(scalarResult.getValue())
            ));
    }

    private void buildMatrixRsp(ParseResult parseResult, ExprQueryRsp response) {
        MetricRspData responseData = new MetricRspData();
        responseData.setResultType(ResultType.MATRIX);
        response.setData(responseData);
        MetricsRangeResult matrixResult = (MetricsRangeResult) parseResult;
        response.setStatus(ResultStatus.SUCCESS);
        responseData.getResult().addAll(matrixResult.getMetricDataList());
    }

    private void buildScalarMatrixRsp(Duration duration, ParseResult parseResult, ExprQueryRsp response) {
        MetricRspData responseData = new MetricRspData();
        responseData.setResultType(ResultType.MATRIX);
        response.setData(responseData);
        ScalarResult scalarResult = (ScalarResult) parseResult;
        response.setStatus(ResultStatus.SUCCESS);
        MetricRangeData metricData = new MetricRangeData();
        metricData.setValues(
            PromOpUtils.buildMatrixValues(duration, formatDoubleValue(scalarResult.getValue())));
        responseData.getResult().add(metricData);
    }

    private static long formatTimestamp2Millis(String timestamp) {
        return Double.valueOf(timestamp).longValue() * 1000;
    }

    private List<LabelName> buildLabelNames(Scope scope,
                                            Column.ValueDataType dataType) {
        List<LabelName> labelNames = new ArrayList<>();
        labelNames.add(LabelName.LAYER);
        labelNames.add(LabelName.SCOPE);
        labelNames.add(LabelName.TOP_N);
        labelNames.add(LabelName.ORDER);
        if (Column.ValueDataType.LABELED_VALUE == dataType) {
            labelNames.add(LabelName.LABELS);
            labelNames.add(LabelName.RELABELS);
        }
        switch (scope) {
            case ServiceInstance:
                labelNames.add(LabelName.SERVICE_INSTANCE);
                labelNames.add(LabelName.PARENT_SERVICE);
                break;
            case Endpoint:
                labelNames.add(LabelName.ENDPOINT);
                labelNames.add(LabelName.PARENT_SERVICE);
                break;
        }
        return labelNames;
    }

    //Only return label names, don't support list the product of all the labels and label values.
    private MetricInfo buildMetaMetricInfo(String metricName,
                                           Scope scope,
                                           Column.ValueDataType dataType) {
        MetricInfo metricInfo = new MetricInfo(metricName);
        metricInfo.getLabels().add(new LabelValuePair(LabelName.LAYER, ""));
        metricInfo.getLabels().add(new LabelValuePair(LabelName.TOP_N, ""));
        metricInfo.getLabels().add(new LabelValuePair(LabelName.ORDER, ""));

        if (Column.ValueDataType.LABELED_VALUE == dataType) {
            metricInfo.getLabels().add(new LabelValuePair(LabelName.LABELS, ""));
            metricInfo.getLabels().add(new LabelValuePair(LabelName.RELABELS, ""));
        }
        switch (scope) {
            case Service:
                metricInfo.getLabels().add(new LabelValuePair(LabelName.SCOPE, Scope.Service.name()));
                metricInfo.getLabels().add(new LabelValuePair(LabelName.SERVICE, ""));
                break;
            case ServiceInstance:
                metricInfo.getLabels().add(new LabelValuePair(LabelName.SCOPE, Scope.ServiceInstance.name()));
                metricInfo.getLabels().add(new LabelValuePair(LabelName.SERVICE_INSTANCE, ""));
                metricInfo.getLabels().add(new LabelValuePair(LabelName.PARENT_SERVICE, ""));
                break;
            case Endpoint:
                metricInfo.getLabels().add(new LabelValuePair(LabelName.SCOPE, Scope.Endpoint.name()));
                metricInfo.getLabels().add(new LabelValuePair(LabelName.ENDPOINT, ""));
                metricInfo.getLabels().add(new LabelValuePair(LabelName.PARENT_SERVICE, ""));
                break;
        }
        return metricInfo;
    }

    private MetricInfo buildMetricInfoFromTraffic(String metricName, Service service) {
        MetricInfo metricInfo = new MetricInfo(metricName);
        metricInfo.getLabels().add(new LabelValuePair(LabelName.SERVICE, service.getName()));
        metricInfo.getLabels().add(new LabelValuePair(LabelName.SCOPE, Scope.Service.name()));
        metricInfo.getLabels().add(new LabelValuePair(LabelName.LAYER, service.getLayers().iterator().next()));
        return metricInfo;
    }

    private MetricInfo buildMetricInfoFromTraffic(String metricName, ServiceInstance instance) {
        MetricInfo metricInfo = new MetricInfo(metricName);
        metricInfo.getLabels()
                  .add(new LabelValuePair(LabelName.SERVICE_INSTANCE, instance.getName()));
        metricInfo.getLabels().add(new LabelValuePair(LabelName.SCOPE, Scope.ServiceInstance.name()));
        return metricInfo;
    }

    private MetricInfo buildMetricInfoFromTraffic(String metricName, Endpoint endpoint) {
        MetricInfo metricInfo = new MetricInfo(metricName);
        metricInfo.getLabels().add(new LabelValuePair(LabelName.ENDPOINT, endpoint.getName()));
        metricInfo.getLabels().add(new LabelValuePair(LabelName.SCOPE, Scope.Endpoint.name()));
        return metricInfo;
    }

    public enum QueryType {
        INSTANT,
        RANGE,
    }
}
