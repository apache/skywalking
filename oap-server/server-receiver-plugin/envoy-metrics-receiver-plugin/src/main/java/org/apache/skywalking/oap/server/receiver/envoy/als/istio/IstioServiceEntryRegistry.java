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

package org.apache.skywalking.oap.server.receiver.envoy.als.istio;

import com.google.common.collect.ImmutableMap;
import com.linecorp.armeria.client.endpoint.dns.DnsAddressEndpointGroup;
import com.linecorp.armeria.client.endpoint.dns.DnsQueryListener;
import io.fabric8.istio.api.networking.v1beta1.ServiceEntry;
import io.fabric8.istio.api.networking.v1beta1.ServiceEntryResolution;
import io.fabric8.istio.api.networking.v1beta1.ServiceEntrySpec;
import io.fabric8.istio.api.networking.v1beta1.WorkloadEntrySpec;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.netty.handler.codec.dns.DnsRecord;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.util.SubnetUtils;
import org.apache.skywalking.library.kubernetes.FailureSummaryLogger;
import org.apache.skywalking.library.kubernetes.IstioServiceEntries;
import org.apache.skywalking.library.kubernetes.KubernetesReadFailedException;
import org.apache.skywalking.library.kubernetes.LookupCache;
import org.apache.skywalking.oap.server.core.config.K8sMetadataReadSettings;
import org.apache.skywalking.oap.server.receiver.envoy.EnvoyMetricReceiverConfig;
import org.apache.skywalking.oap.server.receiver.envoy.als.ServiceMetaInfo;
import org.apache.skywalking.oap.server.receiver.envoy.als.k8s.ServiceNameFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;

/**
 * Resolves an IP to the Istio ServiceEntry it belongs to. A lookup never waits for the API server or for DNS: an IP
 * seen for the first time is unknown while it is read in the background, and is found by the lookups after the read.
 */
@Slf4j
public class IstioServiceEntryRegistry {
    /**
     * Shared by the registries of the HTTP and TCP analyzers, which resolve the same hostnames.
     */
    static final FailureSummaryLogger HOSTNAME_RESOLUTION_FAILURES = new FailureSummaryLogger(
        "Istio ServiceEntry hostname resolution", "lookups use the IPs last resolved, if any", log::warn, log::info);
    private static final Map<EnvoyMetricReceiverConfig, IstioServiceEntryRegistry> SHARED =
        new ConcurrentHashMap<>();

    protected final EnvoyMetricReceiverConfig config;
    protected final ServiceNameFormatter serviceNameFormatter;

    protected final LookupCache<String/* ip */, ServiceMetaInfo> ipServiceMetaInfoMap;
    protected final Map<String, DnsAddressEndpointGroup> hostnameResolvers = new ConcurrentHashMap<>();

    private final Set<String> ignoredNamespaces;

    public IstioServiceEntryRegistry(final EnvoyMetricReceiverConfig config) {
        this.config = config;

        serviceNameFormatter = new ServiceNameFormatter(config.getIstioServiceNameRule());
        ignoredNamespaces = config.getIstioServiceEntryIgnoredNamespaces();

        ipServiceMetaInfoMap = new LookupCache<>(
            this::readServiceMetaInfo, K8sMetadataReadSettings.getRefreshPeriod(),
            K8sMetadataReadSettings.getNotFoundRetryPeriod(), K8sMetadataReadSettings.getAlsCacheTime(),
            K8sMetadataReadSettings::getMalReadQueueSize, K8sMetadataReadSettings::getAlsReadQueueSize);
    }

    /**
     * @return the registry the HTTP and TCP analyzers of {@code config} share, as they look up the same IPs with the
     * same naming rule
     */
    public static IstioServiceEntryRegistry sharedFor(final EnvoyMetricReceiverConfig config) {
        return SHARED.computeIfAbsent(config, IstioServiceEntryRegistry::new);
    }

    /**
     * Starts resolving the hostnames of {@code serviceEntry} that are not resolved yet, when it is resolved by DNS.
     */
    protected void createHostnameResolvers(final ServiceEntry serviceEntry) {
        if (!isConsidered(serviceEntry)
            || !isResolvedByDns(serviceEntry.getSpec())
            || serviceEntry.getSpec().getHosts() == null) {
            return;
        }
        serviceEntry.getSpec()
                    .getHosts()
                    .stream()
                    // A wildcard host never resolves.
                    .filter(host -> host != null && !host.isEmpty() && !host.contains("*"))
                    .forEach(host -> hostnameResolvers.computeIfAbsent(host, this::createHostnameResolver));
    }

    /**
     * Creates the resolution of {@code host}, kept until OAP stops. Armeria's default backoff retries a failing
     * resolution with growing delays; an attempt-limited backoff would instead retry without delay once exhausted.
     * Closing a resolution does not release it: it stays registered in Armeria's shared DNS cache, so a host that
     * comes back reuses its resolution instead of leaking a new one. Failures go to
     * {@link #HOSTNAME_RESOLUTION_FAILURES} in place of Armeria's log line per attempt.
     */
    protected DnsAddressEndpointGroup createHostnameResolver(final String host) {
        return DnsAddressEndpointGroup.builder(host)
                                      .addDnsQueryListeners(
                                          new HostnameResolutionListener(host, HOSTNAME_RESOLUTION_FAILURES))
                                      .build();
    }

    /**
     * Runs in the background. It starts resolving the hostnames of the ServiceEntries not resolved yet; a
     * DNS-resolved ServiceEntry matches once its hostname resolution has completed.
     *
     * @return the composed service of the first matching ServiceEntry, or null when nothing matches
     * @throws KubernetesReadFailedException when the ServiceEntries could not be read
     */
    protected ServiceMetaInfo readServiceMetaInfo(final String ip) {
        final List<ServiceEntry> serviceEntries = IstioServiceEntries.INSTANCE.list();
        serviceEntries.forEach(this::createHostnameResolvers);
        final Optional<ServiceEntry> serviceEntry =
            serviceEntries.stream()
                          .filter(this::isConsidered)
                          .filter(it -> matches(it.getSpec(), ip))
                          .findFirst();

        if (serviceEntry.isEmpty()) {
            log.debug("No corresponding service entry for IP: {}", ip);
            return null;
        }

        log.debug("Composing service meta info from service entry for IP: {}", ip);
        return composeServiceMetaInfo(serviceEntry.get(), ip);
    }

    private boolean isConsidered(final ServiceEntry serviceEntry) {
        return serviceEntry.getMetadata() != null
            && serviceEntry.getSpec() != null
            && !ignoredNamespaces.contains(serviceEntry.getMetadata().getNamespace());
    }

    private static boolean isResolvedByDns(final ServiceEntrySpec spec) {
        return spec.getResolution() != null && (
            spec.getResolution() == ServiceEntryResolution.DNS
                || spec.getResolution() == ServiceEntryResolution.DNS_ROUND_ROBIN);
    }

    private boolean matches(final ServiceEntrySpec spec, final String ip) {
        if (spec.getResolution() == null) {
            log.debug("Unsupported service entry resolution: {}", spec.getResolution());
            return false;
        }
        switch (spec.getResolution()) {
            case STATIC:
                return (spec.getAddresses() != null
                    && spec.getAddresses().stream().anyMatch(address -> isAddressMatched(address, ip)))
                    || (spec.getEndpoints() != null
                    && spec.getEndpoints()
                           .stream()
                           .map(WorkloadEntrySpec::getAddress)
                           .anyMatch(address -> Objects.equals(address, ip)));
            case DNS:
            case DNS_ROUND_ROBIN:
                return spec.getHosts() != null
                    && spec.getHosts()
                           .stream()
                           .map(hostnameResolvers::get)
                           .filter(Objects::nonNull)
                           .filter(resolver -> resolver.whenReady().isDone())
                           .anyMatch(resolver -> resolver.endpoints()
                                                         .stream()
                                                         .anyMatch(endpoint -> Objects.equals(endpoint.ipAddr(), ip)));
            default:
                log.debug("Unsupported service entry resolution: {}", spec.getResolution());
                return false;
        }
    }

    private static boolean isAddressMatched(final String address, final String ip) {
        if (address == null) {
            return false;
        }
        if (address.contains("/")) {
            try {
                return new SubnetUtils(address).getInfo().isInRange(ip);
            } catch (final IllegalArgumentException e) {
                // SubnetUtils supports IPv4 only.
                return false;
            }
        }
        return Objects.equals(ip, address);
    }

    protected List<ServiceMetaInfo.KeyValue> transformLabelsToTags(final ObjectMeta serviceEntryMeta) {
        final var labels = serviceEntryMeta.getLabels();
        final var tags = new ArrayList<ServiceMetaInfo.KeyValue>();
        tags.add(new ServiceMetaInfo.KeyValue("namespace", serviceEntryMeta.getNamespace()));
        if (isNull(labels)) {
            return tags;
        }
        return labels.entrySet()
            .stream()
            .map(each -> new ServiceMetaInfo.KeyValue(each.getKey(), each.getValue()))
            .collect(Collectors.toCollection(() -> tags));
    }

    /**
     * Never waits: an IP not read yet is unknown, and is read in the background for the next lookups.
     */
    public ServiceMetaInfo findService(final String ip) {
        if (ip == null) {
            return config.serviceMetaInfoFactory().unknown();
        }
        return ipServiceMetaInfoMap.find(ip).orElseGet(() -> config.serviceMetaInfoFactory().unknown());
    }

    protected ServiceMetaInfo composeServiceMetaInfo(final ServiceEntry serviceEntry, String ip) {
        final var context = ImmutableMap.<String, Object>of("serviceEntry", serviceEntry);
        final var serviceMetaInfo = new ServiceMetaInfo();

        try {
            serviceMetaInfo.setServiceName(serviceNameFormatter.format(context));
        } catch (Exception e) {
            log.error("Failed to evaluate serviceEntry name.", e);
            final var serviceMetadata = serviceEntry.getMetadata();
            if (isNull(serviceMetadata)) {
                log.warn("Service metadata is null, {}", serviceEntry);
                return config.serviceMetaInfoFactory().unknown();
            }
            serviceMetaInfo.setServiceName(serviceMetadata.getName());
        }
        serviceMetaInfo.setTags(transformLabelsToTags(serviceEntry.getMetadata()));
        serviceMetaInfo.setServiceInstanceName(ip);

        return serviceMetaInfo;
    }

    /**
     * Records the outcome of each resolution of one hostname.
     */
    static final class HostnameResolutionListener implements DnsQueryListener {
        private final String host;
        private final FailureSummaryLogger failures;

        HostnameResolutionListener(final String host, final FailureSummaryLogger failures) {
            this.host = host;
            this.failures = failures;
        }

        @Override
        public void onSuccess(final List<DnsRecord> oldRecords, final List<DnsRecord> newRecords,
                              final String logPrefix) {
            failures.recordSuccess(host);
        }

        @Override
        public void onFailure(final List<DnsRecord> oldRecords, final Throwable cause, final String logPrefix,
                              final long delayMillis, final int attemptsSoFar) {
            failures.recordFailure(host, "type=" + cause.getClass().getSimpleName());
        }
    }
}
