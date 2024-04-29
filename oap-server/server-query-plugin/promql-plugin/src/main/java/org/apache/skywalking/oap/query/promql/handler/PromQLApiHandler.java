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
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.antlr.v4.runtime.misc.ParseCancellationException;
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
import org.apache.skywalking.oap.query.promql.PromQLConfig;
import org.apache.skywalking.oap.query.promql.entity.ErrorType;
import org.apache.skywalking.oap.query.promql.entity.LabelName;
import org.apache.skywalking.oap.query.promql.entity.LabelValuePair;
import org.apache.skywalking.oap.query.promql.entity.MetricInstantData;
import org.apache.skywalking.oap.query.promql.entity.MetricMetadata;
import org.apache.skywalking.oap.query.promql.entity.MetricRangeData;
import org.apache.skywalking.oap.query.promql.entity.TimeValuePair;
import org.apache.skywalking.oap.query.promql.entity.response.BuildInfoRsp;
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
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.manual.endpoint.EndpointTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.service.ServiceTraffic;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataLabel;
import org.apache.skywalking.oap.server.core.query.AggregationQueryService;
import org.apache.skywalking.oap.server.core.query.DurationUtils;
import org.apache.skywalking.oap.server.core.query.MetricDefinition;
import org.apache.skywalking.oap.server.core.query.MetricsMetadataQueryService;
import org.apache.skywalking.oap.server.core.query.MetricsQueryService;
import org.apache.skywalking.oap.server.core.query.RecordQueryService;
import org.apache.skywalking.oap.server.core.query.enumeration.Scope;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.Endpoint;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;
import org.apache.skywalking.oap.server.core.query.type.MetricsValues;
import org.apache.skywalking.oap.server.core.query.type.Service;
import org.apache.skywalking.oap.server.core.query.type.ServiceInstance;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnMetadata;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.promql.rt.grammar.PromQLLexer;
import org.apache.skywalking.promql.rt.grammar.PromQLParser;
import org.joda.time.DateTime;

import static org.apache.skywalking.oap.query.promql.rt.PromOpUtils.formatDoubleValue;

public class PromQLApiHandler {
    private final MetadataQueryV2 metadataQuery;
    private final ModuleManager moduleManager;
    private final PromQLConfig config;
    private MetricsMetadataQueryService metricsMetadataQueryService;
    private MetricsQueryService metricsQueryService;
    private AggregationQueryService aggregationQueryService;
    private RecordQueryService recordQueryService;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public PromQLApiHandler(ModuleManager moduleManager, PromQLConfig config) {
        this.metadataQuery = new MetadataQueryV2(moduleManager);
        this.moduleManager = moduleManager;
        this.config = config;
    }

    private MetricsMetadataQueryService getMetricsMetadataQueryService() {
        if (metricsMetadataQueryService == null) {
            this.metricsMetadataQueryService = moduleManager.find(CoreModule.NAME)
                                                           .provider()
                                                           .getService(MetricsMetadataQueryService.class);
        }
        return metricsMetadataQueryService;
    }

    private MetricsQueryService getMetricsQueryService() {
        if (metricsQueryService == null) {
            this.metricsQueryService = moduleManager.find(CoreModule.NAME)
                                                    .provider()
                                                    .getService(MetricsQueryService.class);
        }
        return metricsQueryService;
    }

    private AggregationQueryService getAggregationQueryService() {
        if (aggregationQueryService == null) {
            this.aggregationQueryService = moduleManager.find(CoreModule.NAME)
                                                        .provider()
                                                        .getService(AggregationQueryService.class);
        }
        return aggregationQueryService;
    }

    private RecordQueryService getRecordQueryService() {
        if (recordQueryService == null) {
            this.recordQueryService = moduleManager.find(CoreModule.NAME)
                                                   .provider()
                                                   .getService(RecordQueryService.class);
        }
        return recordQueryService;
    }

    @Get
    @Path("/api/v1/metadata")
    public HttpResponse metadata(
        @Param("limit") Optional<Integer> limit,
        @Param("metric") Optional<String> metric) throws JsonProcessingException {
        MetadataQueryRsp response = new MetadataQueryRsp();
        response.setStatus(ResultStatus.SUCCESS);
        String regex = metric.orElse("");
        List<MetricDefinition> definitionList = getMetricsMetadataQueryService().listMetrics(regex);
        int inputLimit = limit.orElse(definitionList.size());
        int maxNum = Math.min(inputLimit, definitionList.size());
        for (int i = 0; i < maxNum; i++) {
            List<MetricMetadata> metadataList = new ArrayList<>();
            MetricMetadata metadata = new MetricMetadata(MetricType.GAUGE, "", "");
            metadataList.add(metadata);
            response.getData().put(definitionList.get(i).getName(), metadataList);
        }
        return jsonResponse(response);
    }

    /**
     * If match[] is not present, return general labels.
     * If end time is not present, use current time as default end time.
     * The start param will be ignored, reserve this param to keep consistent with API protocol.
     * Always query with day step.
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
            MatcherSetResult parseResult;
            try {
                parseResult = getMatcherSetResult(match.get());
            } catch (ParseCancellationException e) {
                response.setStatus(ResultStatus.ERROR);
                response.setErrorType(ErrorType.BAD_DATA);
                response.setError(e.getMessage());
                return jsonResponse(response);
            }
            String metricName = parseResult.getMetricName();
            Optional<ValueColumnMetadata.ValueColumn> valueColumn = ValueColumnMetadata.INSTANCE.readValueColumnDefinition(
                metricName);
            if (valueColumn.isPresent()) {
                ValueColumnMetadata.ValueColumn metaData = valueColumn.get();
                Scope scope = Scope.Finder.valueOf(metaData.getScopeId());
                response.getData().addAll(buildLabelNames(scope, metaData));
                if (Column.ValueDataType.LABELED_VALUE == valueColumn.get().getDataType()) {
                    List<MetricsValues> matchedMetrics = getMatcherMetricsValues(parseResult, end);
                    response.getData().addAll(buildLabelNamesFromQuery(matchedMetrics));
                }
                if (metaData.isMultiIntValues()) {
                    response.getData().remove(DataLabel.GENERAL_LABEL_NAME);
                }
            }
        } else {
            Arrays.stream(LabelName.values()).forEach(label -> {
                response.getData().add(label.getLabel());
            });
        }
        response.setStatus(ResultStatus.SUCCESS);
        return jsonResponse(response);
    }

    /**
     * If match[] is not present, return general labels values.
     * If end time is not present, use current time as default end time.
     * The start param will be ignored, reserve this param to keep consistent with API protocol.
     * Always query with day step.
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
        //general labels
        if (LabelName.NAME.getLabel().equals(labelName)) {
            getMetricsMetadataQueryService().listMetrics("").forEach(definition -> {
                response.getData().add(definition.getName());
            });
            return jsonResponse(response);
        } else if (LabelName.LAYER.getLabel().equals(labelName)) {
            for (Layer layer : Layer.values()) {
                response.getData().add(layer.name());
            }
            return jsonResponse(response);
        } else if (LabelName.SCOPE.getLabel().equals(labelName)) {
            for (Scope scope : Scope.values()) {
                response.getData().add(scope.name());
            }
            return jsonResponse(response);
        }

        if (match.isPresent()) {
            MatcherSetResult parseResult;
            try {
                parseResult = getMatcherSetResult(match.get());
            } catch (ParseCancellationException e) {
                response.setStatus(ResultStatus.ERROR);
                response.setErrorType(ErrorType.BAD_DATA);
                response.setError(e.getMessage());
                return jsonResponse(response);
            }
            String metricName = parseResult.getMetricName();
            Optional<ValueColumnMetadata.ValueColumn> valueColumn = ValueColumnMetadata.INSTANCE.readValueColumnDefinition(
                metricName);
            if (valueColumn.isPresent() && Column.ValueDataType.LABELED_VALUE == valueColumn.get().getDataType()) {
                List<MetricsValues> matchedMetrics = getMatcherMetricsValues(parseResult, end);
                response.getData().addAll(buildLabelValuesFromQuery(matchedMetrics, labelName));
            }
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
        Duration duration = DurationUtils.timestamp2Duration(startTS, endTS);
        SeriesQueryRsp response = new SeriesQueryRsp();
        MatcherSetResult parseResult;
        try {
            parseResult = getMatcherSetResult(match);
        } catch (ParseCancellationException e) {
            response.setStatus(ResultStatus.ERROR);
            response.setErrorType(ErrorType.BAD_DATA);
            response.setError(e.getMessage());
            return jsonResponse(response);
        }
        String metricName = parseResult.getMetricName();
        Optional<ValueColumnMetadata.ValueColumn> valueColumn = ValueColumnMetadata.INSTANCE.readValueColumnDefinition(
            metricName);
        if (valueColumn.isPresent()) {
            ValueColumnMetadata.ValueColumn metaData = valueColumn.get();
            Scope scope = Scope.Finder.valueOf(metaData.getScopeId());
            Column.ValueDataType dataType = metaData.getDataType();
            response.getData().add(buildMetaMetricInfo(metricName, scope, dataType));
        } else if (Objects.equals(metricName, ServiceTraffic.INDEX_NAME)) {
            String serviceName = parseResult.getLabelMap().get(LabelName.SERVICE.getLabel());
            if (StringUtil.isNotBlank(serviceName)) {
                Service service = metadataQuery.findService(serviceName);
                response.getData().add(buildMetricInfoFromTraffic(metricName, service));
            } else {
                List<Service> services = metadataQuery.listServices(
                    parseResult.getLabelMap().get(LabelName.LAYER.getLabel()));
                services.forEach(service -> {
                    response.getData().add(buildMetricInfoFromTraffic(metricName, service));
                });
            }
        } else if (Objects.equals(metricName, InstanceTraffic.INDEX_NAME)) {
            String serviceName = parseResult.getLabelMap().get(LabelName.SERVICE.getLabel());
            String layer = parseResult.getLabelMap().get(LabelName.LAYER.getLabel());
            List<ServiceInstance> instances = metadataQuery.listInstances(
                duration, IDManager.ServiceID.buildId(serviceName, Layer.valueOf(layer).isNormal()));
            instances.forEach(instance -> {
                response.getData().add(buildMetricInfoFromTraffic(metricName, instance));
            });
        } else if (Objects.equals(metricName, EndpointTraffic.INDEX_NAME)) {
            String serviceName = parseResult.getLabelMap().get(LabelName.SERVICE.getLabel());
            String layer = parseResult.getLabelMap().get(LabelName.LAYER.getLabel());
            String keyword = parseResult.getLabelMap().getOrDefault(LabelName.KEYWORD.getLabel(), "");
            String limit = parseResult.getLabelMap().getOrDefault(LabelName.LIMIT.getLabel(), "100");
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
        Duration duration = DurationUtils.timestamp2Duration(startTS, endTS);
        ExprQueryRsp response = new ExprQueryRsp();

        PromQLLexer lexer = new PromQLLexer(
            CharStreams.fromString(query));
        lexer.addErrorListener(new ParseErrorListener());
        PromQLParser parser = new PromQLParser(new CommonTokenStream(lexer));
        parser.addErrorListener(new ParseErrorListener());
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
            getMetricsQueryService(), getRecordQueryService(), getAggregationQueryService(), duration, QueryType.INSTANT);
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
        Duration duration = DurationUtils.timestamp2Duration(startTS, endTS);
        ExprQueryRsp response = new ExprQueryRsp();
        PromQLLexer lexer = new PromQLLexer(
            CharStreams.fromString(query));
        lexer.addErrorListener(new ParseErrorListener());
        PromQLParser parser = new PromQLParser(new CommonTokenStream(lexer));
        parser.addErrorListener(new ParseErrorListener());
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
            getMetricsQueryService(), getRecordQueryService(), getAggregationQueryService(), duration, QueryType.RANGE);
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

    @Get
    @Post
    @Path("/api/v1/status/buildinfo")
    public HttpResponse buildInfo() throws IOException {
        BuildInfoRsp buildInfoRsp = new BuildInfoRsp();
        buildInfoRsp.setStatus(ResultStatus.SUCCESS);
        buildInfoRsp.getData().setVersion(config.getBuildInfoVersion());
        buildInfoRsp.getData().setRevision(config.getBuildInfoRevision());
        buildInfoRsp.getData().setBranch(config.getBuildInfoBranch());
        buildInfoRsp.getData().setBuildUser(config.getBuildInfoBuildUser());
        buildInfoRsp.getData().setBuildDate(config.getBuildInfoBuildDate());
        buildInfoRsp.getData().setGoVersion(config.getBuildInfoGoVersion());
        return jsonResponse(buildInfoRsp);
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

    private List<String> buildLabelNames(Scope scope, ValueColumnMetadata.ValueColumn metaData) {
        List<String> labelNames = new ArrayList<>();
        labelNames.add(LabelName.LAYER.getLabel());
        labelNames.add(LabelName.SERVICE.getLabel());
        labelNames.add(LabelName.TOP_N.getLabel());
        labelNames.add(LabelName.ORDER.getLabel());
        if (metaData.isMultiIntValues()) {
            labelNames.add(LabelName.LABELS.getLabel());
            labelNames.add(LabelName.RELABELS.getLabel());
        }
        switch (scope) {
            case ServiceInstance:
                labelNames.add(LabelName.SERVICE_INSTANCE.getLabel());
                labelNames.add(LabelName.PARENT_SERVICE.getLabel());
                break;
            case Endpoint:
                labelNames.add(LabelName.ENDPOINT.getLabel());
                labelNames.add(LabelName.PARENT_SERVICE.getLabel());
                break;
        }
        return labelNames;
    }

    private List<String> buildLabelNamesFromQuery(List<MetricsValues> metricsValues) {
        Set<String> labelNames = new LinkedHashSet<>();
        metricsValues.forEach(metricsValue -> {
            DataLabel dataLabel = new DataLabel();
            dataLabel.put(metricsValue.getLabel());
            labelNames.addAll(dataLabel.keySet());
        });
        return Arrays.asList(labelNames.toArray(new String[0]));
    }

    private List<String> buildLabelValuesFromQuery(List<MetricsValues> metricsValues, String labelName) {
        Set<String> labelValues = new LinkedHashSet<>();
        metricsValues.forEach(metricsValue -> {
            DataLabel dataLabel = new DataLabel();
            dataLabel.put(metricsValue.getLabel());
            labelValues.add(dataLabel.get(labelName));
        });
        return Arrays.asList(labelValues.toArray(new String[0]));
    }


    //Only return label names, don't support list the product of all the labels and label values.
    private MetricInfo buildMetaMetricInfo(String metricName,
                                           Scope scope,
                                           Column.ValueDataType dataType) {
        MetricInfo metricInfo = new MetricInfo(metricName);
        metricInfo.getLabels().add(new LabelValuePair(LabelName.LAYER.getLabel(), ""));
        metricInfo.getLabels().add(new LabelValuePair(LabelName.TOP_N.getLabel(), ""));
        metricInfo.getLabels().add(new LabelValuePair(LabelName.ORDER.getLabel(), ""));

        if (Column.ValueDataType.LABELED_VALUE == dataType) {
            metricInfo.getLabels().add(new LabelValuePair(LabelName.LABELS.getLabel(), ""));
            metricInfo.getLabels().add(new LabelValuePair(LabelName.RELABELS.getLabel(), ""));
        }
        switch (scope) {
            case Service:
                metricInfo.getLabels().add(new LabelValuePair(LabelName.SCOPE.getLabel(), Scope.Service.name()));
                metricInfo.getLabels().add(new LabelValuePair(LabelName.SERVICE.getLabel(), ""));
                break;
            case ServiceInstance:
                metricInfo.getLabels().add(new LabelValuePair(LabelName.SCOPE.getLabel(), Scope.ServiceInstance.name()));
                metricInfo.getLabels().add(new LabelValuePair(LabelName.SERVICE.getLabel(), ""));
                metricInfo.getLabels().add(new LabelValuePair(LabelName.SERVICE_INSTANCE.getLabel(), ""));
                metricInfo.getLabels().add(new LabelValuePair(LabelName.PARENT_SERVICE.getLabel(), ""));
                break;
            case Endpoint:
                metricInfo.getLabels().add(new LabelValuePair(LabelName.SCOPE.getLabel(), Scope.Endpoint.name()));
                metricInfo.getLabels().add(new LabelValuePair(LabelName.SERVICE.getLabel(), ""));
                metricInfo.getLabels().add(new LabelValuePair(LabelName.ENDPOINT.getLabel(), ""));
                metricInfo.getLabels().add(new LabelValuePair(LabelName.PARENT_SERVICE.getLabel(), ""));
                break;
        }
        return metricInfo;
    }

    private MetricInfo buildMetricInfoFromTraffic(String metricName, Service service) {
        MetricInfo metricInfo = new MetricInfo(metricName);
        metricInfo.getLabels().add(new LabelValuePair(LabelName.SERVICE.getLabel(), service.getName()));
        metricInfo.getLabels().add(new LabelValuePair(LabelName.SCOPE.getLabel(), Scope.Service.name()));
        metricInfo.getLabels().add(new LabelValuePair(LabelName.LAYER.getLabel(), service.getLayers().iterator().next()));
        return metricInfo;
    }

    private MetricInfo buildMetricInfoFromTraffic(String metricName, ServiceInstance instance) {
        MetricInfo metricInfo = new MetricInfo(metricName);
        metricInfo.getLabels()
                  .add(new LabelValuePair(LabelName.SERVICE_INSTANCE.getLabel(), instance.getName()));
        metricInfo.getLabels().add(new LabelValuePair(LabelName.SCOPE.getLabel(), Scope.ServiceInstance.name()));
        return metricInfo;
    }

    private MetricInfo buildMetricInfoFromTraffic(String metricName, Endpoint endpoint) {
        MetricInfo metricInfo = new MetricInfo(metricName);
        metricInfo.getLabels().add(new LabelValuePair(LabelName.ENDPOINT.getLabel(), endpoint.getName()));
        metricInfo.getLabels().add(new LabelValuePair(LabelName.SCOPE.getLabel(), Scope.Endpoint.name()));
        return metricInfo;
    }

    private Duration getDayDurationFromTimestamp(long timeTS) {
        Duration duration = new Duration();
        DateTime dt = new DateTime(timeTS);
        duration.setStep(Step.DAY);
        duration.setStart(dt.toString(DurationUtils.YYYY_MM_DD));
        duration.setEnd(dt.toString(DurationUtils.YYYY_MM_DD));
        return duration;
    }

    private MatcherSetResult getMatcherSetResult(String matchString) {
        PromQLLexer lexer = new PromQLLexer(
            CharStreams.fromString(matchString));
        lexer.addErrorListener(new ParseErrorListener());
        PromQLParser parser = new PromQLParser(new CommonTokenStream(lexer));
        parser.addErrorListener(new ParseErrorListener());
        ParseTree tree = parser.expression();
        PromQLMatchVisitor visitor = new PromQLMatchVisitor();
        return visitor.visit(tree);
    }

    private List<MetricsValues> getMatcherMetricsValues(MatcherSetResult parseResult,  Optional<String> end) throws IOException {
        String metricName = parseResult.getMetricName();
        List<KeyValue> matchLabels = parseResult.getLabelMap().entrySet().stream().map(entry -> {
            KeyValue keyValue = new KeyValue();
            keyValue.setKey(entry.getKey());
            keyValue.setValue(entry.getValue());
            return keyValue;
        }).collect(Collectors.toList());
        long endTS = System.currentTimeMillis();
        if (end.isPresent()) {
            endTS = formatTimestamp2Millis(end.get());
        }
        Duration duration = getDayDurationFromTimestamp(endTS);
        return getMetricsQueryService().readLabeledMetricsValuesWithoutEntity(metricName, matchLabels, duration);
    }

    public enum QueryType {
        INSTANT,
        RANGE,
    }
}
