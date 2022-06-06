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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import org.apache.skywalking.oap.query.graphql.type.InternalLog;
import org.apache.skywalking.oap.query.graphql.type.LogAdapter;
import org.apache.skywalking.oap.query.graphql.type.OndemandContainergQueryCondition;
import org.apache.skywalking.oap.query.graphql.type.OndemandLogQueryCondition;
import org.apache.skywalking.oap.query.graphql.type.PodContainers;
import org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic.PropertyUtil;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.Attribute;
import org.apache.skywalking.oap.server.core.query.type.Log;
import org.apache.skywalking.oap.server.core.query.type.Logs;
import org.apache.skywalking.oap.server.core.query.type.ServiceInstance;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import graphql.kickstart.tools.GraphQLQueryResolver;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.util.Config;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class OndemandLogQuery implements GraphQLQueryResolver {
    private final Gson gson = new Gson();
    private final Type responseType = new TypeToken<Map<String, Object>>() {
    }.getType();
    private CoreV1Api kApi;

    private final MetadataQueryV2 metadataQuery;

    public PodContainers listContainers(final OndemandContainergQueryCondition condition)
        throws IOException {
        final ServiceInstance instance =
            metadataQuery.getInstance(condition.getServiceInstanceId());
        final Map<String, String> attributesMap = convertInstancePropertiesToMap(instance);
        final String ns = attributesMap.get(PropertyUtil.NAMESPACE);
        final String pod = attributesMap.get(PropertyUtil.POD);
        return listContainers(ns, pod);
    }

    public Logs ondemandPodLogs(final OndemandLogQueryCondition condition)
        throws IOException {
        final ServiceInstance instance =
            metadataQuery.getInstance(condition.getServiceInstanceId());
        final Map<String, String> attributesMap = convertInstancePropertiesToMap(instance);
        final String ns = attributesMap.get(PropertyUtil.NAMESPACE);
        final String pod = attributesMap.get(PropertyUtil.POD);
        return ondemandPodLogs(ns, pod, condition);
    }

    protected Map<String, String> convertInstancePropertiesToMap(final ServiceInstance instance) {
        if (instance == null) {
            return Collections.emptyMap();
        }
        final List<Attribute> attributes = instance.getAttributes();
        if (attributes == null || attributes.isEmpty()) {
            return Collections.emptyMap();
        }
        final Map<String, String> attributesMap =
            attributes
                .stream()
                .collect(Collectors.toMap(Attribute::getName, Attribute::getValue));
        if (!attributesMap.containsKey(PropertyUtil.NAMESPACE)
            || !attributesMap.containsKey(PropertyUtil.POD)) {
            return Collections.emptyMap();
        }
        return attributesMap;
    }

    public PodContainers listContainers(
        final String namespace,
        final String podName) throws IOException {
        try {
            if (Strings.isNullOrEmpty(namespace) || Strings.isNullOrEmpty(podName)) {
                return new PodContainers()
                    .setErrorReason("namespace and podName can't be null or empty");
            }
            final V1Pod pod = kApi().readNamespacedPod(podName, namespace, null);
            final V1PodSpec spec = pod.getSpec();
            if (isNull(spec)) {
                return new PodContainers().setErrorReason("No pod spec can be found");
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

            return new PodContainers().setContainers(containers);
        } catch (ApiException e) {
            log.error("Failed to list containers from Kubernetes, {}", e.getResponseBody(), e);

            if (!Strings.isNullOrEmpty(e.getResponseBody())) {
                Map<String, Object> responseBody = gson.fromJson(e.getResponseBody(), responseType);
                String message = responseBody.getOrDefault("message", e.getCode()).toString();
                return new PodContainers().setErrorReason(message);
            }
            return new PodContainers().setErrorReason(e.getMessage() + ": " + e.getCode());
        }
    }

    public Logs ondemandPodLogs(
        final String namespace,
        final String podName,
        final OndemandLogQueryCondition condition) throws IOException {
        if (Strings.isNullOrEmpty(namespace) || Strings.isNullOrEmpty(podName)) {
            return new Logs().setErrorReason("namespace and podName can't be null or empty");
        }
        try {
            final V1Pod pod = kApi().readNamespacedPod(podName, namespace, null);
            final V1ObjectMeta podMetadata = pod.getMetadata();
            if (isNull(podMetadata)) {
                return new Logs().setErrorReason("No pod metadata can be found");
            }
            final V1PodSpec spec = pod.getSpec();
            if (isNull(spec)) {
                return new Logs().setErrorReason("No pod spec can be found");
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

            if (!Strings.isNullOrEmpty(e.getResponseBody())) {
                Map<String, Object> responseBody = gson.fromJson(e.getResponseBody(), responseType);
                String message = responseBody.getOrDefault("message", e.getCode()).toString();
                return new Logs().setErrorReason(message);
            }
            return new Logs().setErrorReason(e.getMessage() + ": " + e.getCode());
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
