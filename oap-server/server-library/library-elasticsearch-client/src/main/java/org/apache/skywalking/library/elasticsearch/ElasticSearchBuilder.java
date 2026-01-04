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

package org.apache.skywalking.library.elasticsearch;

import com.google.common.collect.ImmutableList;
import com.linecorp.armeria.client.ClientFactory;
import com.linecorp.armeria.client.ClientFactoryBuilder;
import com.linecorp.armeria.client.Endpoint;
import com.linecorp.armeria.client.endpoint.EndpointGroup;
import com.linecorp.armeria.client.endpoint.healthcheck.HealthCheckedEndpointGroup;
import com.linecorp.armeria.client.endpoint.healthcheck.HealthCheckedEndpointGroupBuilder;
import com.linecorp.armeria.client.logging.LoggingClient;
import com.linecorp.armeria.common.SessionProtocol;
import com.linecorp.armeria.common.auth.AuthToken;

import io.netty.channel.nio.NioEventLoopGroup;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import org.apache.skywalking.oap.server.library.util.StringUtil;

@Slf4j
public final class ElasticSearchBuilder {
    private static final int NUM_PROC = Runtime.getRuntime().availableProcessors();

    private SessionProtocol protocol = SessionProtocol.HTTP;

    private String username;

    private String password;

    private Duration healthCheckRetryInterval = Duration.ofSeconds(30);

    private final ImmutableList.Builder<String> endpoints = ImmutableList.builder();

    private String trustStorePath;

    private String trustStorePass;

    private String keyStorePath;

    private String keyStorePass;

    private Duration responseTimeout = Duration.ofSeconds(15);

    private Duration connectTimeout = Duration.ofMillis(500);

    private Duration socketTimeout = Duration.ofSeconds(30);

    private Consumer<Boolean> healthyListener;

    private int numHttpClientThread;

    public ElasticSearchBuilder protocol(String protocol) {
        checkArgument(StringUtil.isNotBlank(protocol), "protocol cannot be blank");
        this.protocol = SessionProtocol.of(protocol);
        return this;
    }

    public ElasticSearchBuilder username(String username) {
        this.username = requireNonNull(username, "username");
        return this;
    }

    public ElasticSearchBuilder password(String password) {
        this.password = requireNonNull(password, "password");
        return this;
    }

    public ElasticSearchBuilder endpoints(Iterable<String> endpoints) {
        requireNonNull(endpoints, "endpoints");
        this.endpoints.addAll(endpoints);
        return this;
    }

    public ElasticSearchBuilder endpoints(String... endpoints) {
        return endpoints(Arrays.asList(endpoints));
    }

    public ElasticSearchBuilder healthCheckRetryInterval(Duration healthCheckRetryInterval) {
        requireNonNull(healthCheckRetryInterval, "healthCheckRetryInterval");
        this.healthCheckRetryInterval = healthCheckRetryInterval;
        return this;
    }

    public ElasticSearchBuilder trustStorePath(String trustStorePath) {
        requireNonNull(trustStorePath, "trustStorePath");
        this.trustStorePath = trustStorePath;
        return this;
    }

    public ElasticSearchBuilder trustStorePass(String trustStorePass) {
        requireNonNull(trustStorePass, "trustStorePass");
        this.trustStorePass = trustStorePass;
        return this;
    }

    public ElasticSearchBuilder keyStorePath(String keyStorePath) {
        requireNonNull(keyStorePath, "keyStorePath");
        this.keyStorePath = keyStorePath;
        return this;
    }

    public ElasticSearchBuilder keyStorePass(String keyStorePass) {
        requireNonNull(keyStorePass, "keyStorePass");
        this.keyStorePass = keyStorePass;
        return this;
    }

    public ElasticSearchBuilder connectTimeout(int connectTimeout) {
        checkArgument(connectTimeout > 0, "connectTimeout must be positive");
        this.connectTimeout = Duration.ofMillis(connectTimeout);
        return this;
    }

    public ElasticSearchBuilder responseTimeout(int responseTimeout) {
        checkArgument(responseTimeout >= 0, "responseTimeout must be 0 or positive");
        this.responseTimeout = Duration.ofMillis(responseTimeout);
        return this;
    }

    public ElasticSearchBuilder socketTimeout(int socketTimeout) {
        checkArgument(socketTimeout > 0, "socketTimeout must be positive");
        this.socketTimeout = Duration.ofMillis(socketTimeout);
        return this;
    }

    public ElasticSearchBuilder healthyListener(Consumer<Boolean> healthyListener) {
        requireNonNull(healthyListener, "healthyListener");
        this.healthyListener = healthyListener;
        return this;
    }

    public ElasticSearchBuilder numHttpClientThread(int numHttpClientThread) {
        this.numHttpClientThread = numHttpClientThread;
        return this;
    }

    @SneakyThrows
    public ElasticSearch build() {
        final List<Endpoint> endpoints =
            this.endpoints.build().stream()
                          .filter(StringUtil::isNotBlank)
                          .map(Endpoint::parse)
                          .collect(Collectors.toList());
        final ClientFactoryBuilder factoryBuilder =
            ClientFactory.builder()
                         .connectTimeout(connectTimeout)
                         .idleTimeout(socketTimeout)
                         .useHttp2Preface(false)
                         .workerGroup(new NioEventLoopGroup(numHttpClientThread > 0 ? numHttpClientThread : NUM_PROC), true);

        // Configure SSL/TLS with optional mutual TLS (client certificate authentication)
        final boolean hasTrustStore = StringUtil.isNotBlank(trustStorePath);
        final boolean hasKeyStore = StringUtil.isNotBlank(keyStorePath);

        if (hasTrustStore || hasKeyStore) {
            factoryBuilder.tlsCustomizer(sslContextBuilder -> {
                try {
                    // Configure trust store for server certificate validation
                    if (hasTrustStore) {
                        log.info("Loading truststore from: {}", trustStorePath);
                        final var trustManagerFactory =
                            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                        final var truststoreType = detectKeystoreType(trustStorePath);
                        log.info("Detected truststore type: {} for file: {}", truststoreType, trustStorePath);
                        final var truststore = KeyStore.getInstance(truststoreType);
                        try (final InputStream is = Files.newInputStream(Paths.get(trustStorePath))) {
                            truststore.load(is, trustStorePass != null ? trustStorePass.toCharArray() : null);
                            log.info("Successfully loaded truststore from: {}", trustStorePath);
                        }
                        trustManagerFactory.init(truststore);
                        sslContextBuilder.trustManager(trustManagerFactory);
                        log.info("Truststore configured successfully");
                    }

                    // Configure key store for client certificate authentication (mutual TLS)
                    if (hasKeyStore) {
                        log.info("Loading keystore from: {}", keyStorePath);
                        final var keyManagerFactory =
                            KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());

                        // Detect keystore type from file extension or try both PKCS12 and JKS
                        final var keystoreType = detectKeystoreType(keyStorePath);
                        log.info("Detected keystore type: {} for file: {}", keystoreType, keyStorePath);
                        final var keystore = KeyStore.getInstance(keystoreType);

                        try (final var is = Files.newInputStream(Paths.get(keyStorePath))) {
                            keystore.load(is, keyStorePass != null ? keyStorePass.toCharArray() : null);
                            log.info("Successfully loaded keystore from: {}", keyStorePath);
                        }

                        // Log certificate details for troubleshooting
                        logCertificateDetails(keystore, keyStorePath);

                        keyManagerFactory.init(keystore, keyStorePass != null ? keyStorePass.toCharArray() : null);
                        sslContextBuilder.keyManager(keyManagerFactory);

                        log.info("Client certificate authentication enabled with keystore: {} (type: {})", 
                                 keyStorePath, keystoreType);
                    }
                } catch (Exception e) {
                    log.error("Failed to configure SSL/TLS context", e);
                    throw new RuntimeException("Failed to configure SSL/TLS context: " + e.getMessage(), e);
                }
            });
        }

        final ClientFactory clientFactory = factoryBuilder.build();

        final HealthCheckedEndpointGroupBuilder endpointGroupBuilder =
            HealthCheckedEndpointGroup.builder(EndpointGroup.of(endpoints), "_cluster/health")
                                      .protocol(protocol)
                                      .useGet(true)
                                      .clientFactory(clientFactory)
                                      .retryInterval(healthCheckRetryInterval)
                                      .withClientOptions(options -> {
                                          options.decorator(
                                              LoggingClient.builder()
                                                           .logger(log)
                                                           .newDecorator());
                                          options.decorator((delegate, ctx, req) -> {
                                              ctx.logBuilder().name("health-check");
                                              return delegate.execute(ctx, req);
                                          });
                                          return options;
                                      });
        if (StringUtil.isNotBlank(username) && StringUtil.isNotBlank(password)) {
            endpointGroupBuilder.auth(AuthToken.ofBasic(username, password));
        }
        final HealthCheckedEndpointGroup endpointGroup = endpointGroupBuilder.build();

        return new ElasticSearch(
            protocol,
            username,
            password,
            endpointGroup,
            clientFactory,
            healthyListener,
            responseTimeout
        );
    }

    /**
     * Detects keystore type from file extension.
     * Defaults to PKCS12 as it's the modern standard and recommended format.
     */
    private static String detectKeystoreType(String keyStorePath) {
        if (keyStorePath == null) {
            return "PKCS12";
        }

        String lowerPath = keyStorePath.toLowerCase();
        if (lowerPath.endsWith(".jks")) {
            return "JKS";
        } else if (lowerPath.endsWith(".p12") || lowerPath.endsWith(".pfx")) {
            return "PKCS12";
        }

        // Default to PKCS12 for unknown extensions
        log.info("Unknown keystore extension for {}, defaulting to PKCS12 format", keyStorePath);
        return "PKCS12";
    }

    /**
     * Logs certificate details from the keystore for troubleshooting purposes.
     */
    @SneakyThrows
    private static void logCertificateDetails(KeyStore keystore, String keyStorePath) {
        try {
            final Enumeration<String> aliases = keystore.aliases();
            int certCount = 0;
            while (aliases.hasMoreElements()) {
                final String alias = aliases.nextElement();
                certCount++;
                log.info("Keystore entry [{}]: alias='{}', isKeyEntry={}, isCertificateEntry={}", 
                         certCount, alias, keystore.isKeyEntry(alias), keystore.isCertificateEntry(alias));

                if (keystore.isKeyEntry(alias)) {
                    final java.security.cert.Certificate cert = keystore.getCertificate(alias);
                    if (cert instanceof X509Certificate) {
                        final X509Certificate x509Cert = (X509Certificate) cert;
                        log.info("  Certificate subject: {}", x509Cert.getSubjectDN());
                        log.info("  Certificate issuer: {}", x509Cert.getIssuerDN());
                        log.info("  Certificate serial number: {}", x509Cert.getSerialNumber());
                        log.info("  Certificate valid from: {} to {}", 
                                 x509Cert.getNotBefore(), x509Cert.getNotAfter());
                        log.info("  Certificate algorithm: {}", x509Cert.getSigAlgName());
                    }
                } else if (keystore.isCertificateEntry(alias)) {
                    final java.security.cert.Certificate cert = keystore.getCertificate(alias);
                    if (cert instanceof X509Certificate) {
                        final X509Certificate x509Cert = (X509Certificate) cert;
                        log.info("  Certificate subject: {}", x509Cert.getSubjectDN());
                        log.info("  Certificate issuer: {}", x509Cert.getIssuerDN());
                    }
                }
            }
            if (certCount == 0) {
                log.warn("No certificates found in keystore: {}", keyStorePath);
            } else {
                log.info("Total entries in keystore: {}", certCount);
            }
        } catch (Exception e) {
            log.warn("Failed to log certificate details from keystore: {}", keyStorePath, e);
        }
    }
}
