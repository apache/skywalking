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

package org.apache.skywalking.oap.query.graphql.resolver;

import static java.util.Comparator.comparing;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import org.apache.skywalking.oap.query.graphql.type.InternalLog;
import org.apache.skywalking.oap.query.graphql.type.LogAdapter;
import org.apache.skywalking.oap.query.graphql.type.OndemandLogQueryCondition;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.Log;
import org.apache.skywalking.oap.server.core.query.type.Logs;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import graphql.kickstart.tools.GraphQLQueryResolver;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1NamespaceList;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.util.Config;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OndemandLogQuery implements GraphQLQueryResolver {
    private final Gson gson = new Gson();
    private final Type responseType = new TypeToken<Map<String, Object>>() {
    }.getType();
    private CoreV1Api kApi;

    public List<String> listNamespaces() throws IOException {
        try {
            final V1NamespaceList nsList =
                kApi().listNamespace(null, null, null, null, null, null, null, null, null, null);
            return nsList
                .getItems()
                .stream()
                .map(V1Namespace::getMetadata)
                .filter(Objects::nonNull)
                .map(V1ObjectMeta::getName)
                .collect(Collectors.toList());
        } catch (ApiException e) {
            log.error("Failed to list namespaces from Kubernetes, {}", e.getResponseBody(), e);

            Map<String, Object> responseBody = gson.fromJson(e.getResponseBody(), responseType);
            String message = responseBody.getOrDefault("message", e.getCode()).toString();
            throw new RuntimeException(message);
        }
    }

    public List<String> listContainers(final OndemandLogQueryCondition condition)
        throws IOException {
        final String ns = condition.getNamespace();
        final IDManager.ServiceInstanceID.InstanceIDDefinition instanceIDDefinition =
            IDManager.ServiceInstanceID.analysisId(condition.getServiceInstanceId());
        final String instanceName = instanceIDDefinition.getName();

        try {
            final V1Pod pod = kApi().readNamespacedPod(instanceName, ns, null);
            final V1PodSpec spec = pod.getSpec();
            if (isNull(spec)) {
                throw new RuntimeException(String.format("No spec: %s:%s", ns, instanceName));
            }

            final List<String> containers = spec.getContainers().stream()
                .map(V1Container::getName)
                .collect(Collectors.toList());
            if (nonNull(spec.getInitContainers())) {
                final List<String> init = spec.getInitContainers().stream()
                    .map(V1Container::getName)
                    .collect(Collectors.toList());
                containers.addAll(init);
            }

            return containers;
        } catch (ApiException e) {
            log.error("Failed to list containers from Kubernetes, {}", e.getResponseBody(), e);

            Map<String, Object> responseBody = gson.fromJson(e.getResponseBody(), responseType);
            String message = responseBody.getOrDefault("message", e.getCode()).toString();
            throw new RuntimeException(message);
        }
    }

    public Logs ondemandPodLogs(OndemandLogQueryCondition condition)
        throws IOException {
        final String ns = condition.getNamespace();
        final IDManager.ServiceInstanceID.InstanceIDDefinition instanceIDDefinition =
            IDManager.ServiceInstanceID.analysisId(condition.getServiceInstanceId());
        final String instanceName = instanceIDDefinition.getName();

        try {
            final V1Pod pod = kApi().readNamespacedPod(instanceName, ns, null);
            final V1ObjectMeta podMetadata = pod.getMetadata();
            if (isNull(podMetadata)) {
                throw new RuntimeException(
                    String.format("No such instance: %s:%s", ns, instanceName));
            }
            final V1PodSpec spec = pod.getSpec();
            if (isNull(spec)) {
                throw new RuntimeException(String.format("No spec: %s:%s", ns, instanceName));
            }

            final Duration duration = new Duration();
            duration.setStart(condition.getDuration().getStart());
            duration.setEnd(condition.getDuration().getEnd());
            duration.setStep(condition.getDuration().getStep());
            final long since = duration.getStartTimestamp() / 1000;
            final String container = condition.getContainer();

            final String podLog = kApi().readNamespacedPodLog(
                podMetadata.getName(),
                podMetadata.getNamespace(),
                container,
                false, null, null, null, null, (int) since, null, true);
            final List<InternalLog> logs = Splitter.on("\n").omitEmptyStrings()
                .splitToList(Strings.nullToEmpty(podLog))
                .stream()
                .filter(StringUtil::isNotBlank)
                .map(it -> InternalLog.builder()
                    .line(it)
                    .container(container)
                    .build())
                .collect(Collectors.toList());

            final List<Log> filtered = filter(condition, logs);

            final List<Log> limited =
                filtered
                    .stream()
                    .limit(10000)
                    .collect(Collectors.toList());
            final Logs result = new Logs();
            result.getLogs().addAll(limited);
            return result;

        } catch (ApiException e) {
            log.error("Failed to fetch logs from Kubernetes, {}", e.getResponseBody(), e);

            Map<String, Object> responseBody = gson.fromJson(e.getResponseBody(), responseType);
            String message = responseBody.getOrDefault("message", e.getCode()).toString();
            throw new RuntimeException(message);
        }
    }

    private List<Log> filter(
        final OndemandLogQueryCondition request,
        final List<InternalLog> logs) {
        final Duration duration = new Duration();
        duration.setStart(request.getDuration().getStart());
        duration.setEnd(request.getDuration().getEnd());
        duration.setStep(request.getDuration().getStep());
        final long since = duration.getStartTimestamp() / 1000;
        final long to = duration.getEndTimestamp() / 1000;

        final List<String> inclusions = request.getKeywordsOfContent();
        final Predicate<Log> inclusivePredicate = l -> inclusions.isEmpty() ||
            inclusions.stream().anyMatch(k -> l.getContent().matches(k));

        final List<String> exclusions = request.getExcludingKeywordsOfContent();
        final Predicate<Log> exclusivePredicate = l -> exclusions.isEmpty() ||
            exclusions.stream().noneMatch(k -> l.getContent().matches(k));

        return logs.stream()
            .map(LogAdapter::new).map(LogAdapter::adapt)
            .filter(inclusivePredicate)
            .filter(exclusivePredicate)
            .filter(it -> it.getTimestamp() >= since)
            .filter(it -> it.getTimestamp() <= to)
            .sorted(comparing(Log::getTimestamp))
            .collect(Collectors.toList());
    }

    private CoreV1Api kApi() throws IOException {
        if (kApi == null) {
            Configuration.setDefaultApiClient(
                Config
                    .defaultClient()
                    .setReadTimeout(30000)
                    .setWriteTimeout(30000)
                    .setConnectTimeout(303000));

            kApi = new CoreV1Api();
        }
        return kApi;
    }
}
