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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.Getter;
import org.apache.skywalking.oap.server.core.CoreModuleConfig;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.MetricsExtension;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.config.NoneStream;
import org.apache.skywalking.oap.server.core.analysis.management.ManagementData;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.analysis.worker.NoneStreamProcessor;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.IMetricsDAO;
import org.apache.skywalking.oap.server.core.storage.IRecordDAO;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.annotation.Storage;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelInstaller;
import org.apache.skywalking.oap.server.core.storage.model.StorageManipulationOpt;
import org.apache.skywalking.oap.server.core.storage.model.StorageModels;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.client.request.PrepareRequest;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.BatchProcessEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.BatchProcessEsDAOITExt;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.ElasticSearchConverter;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexNames;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.RoutingUtils;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.StorageEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.StorageEsInstaller;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.none.MetricsCreatorNoop;
import org.apache.skywalking.oap.server.telemetry.none.NoneTelemetryProvider;
import org.apache.skywalking.oap.server.testing.util.ReflectUtil;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * An Elasticsearch or OpenSearch server on a test container, with OAP's storage set up on it the way
 * {@link StorageModuleElasticsearchProvider} sets it up — the client, the index installer and the bulk writer — for
 * the ITs that read what OAP's own write path stored back through OAP's query DAOs.
 *
 * <p>The ITs of every area share one server per version and mode, so each IT writes its indices under a namespace of
 * its own, named after its class. A write is refreshed before it returns, as the ITs read right after they write and
 * OAP's refresh interval is 30 seconds.
 *
 * <p>{@link IndexController} and {@link IndexNames} are JVM-wide, so {@link #connect} sets them for the mode of the IT
 * class connecting, and IT classes must run one after another.
 */
public final class ElasticSearchQueryITSupport implements Closeable {
    public enum Mode {
        /**
         * OAP's defaults: every metric in one physical index, every record that is not a super dataset in another,
         * and documents routed by their id.
         */
        DEFAULT,
        /**
         * One physical index per model, and documents routed by their model's routing column over more than one
         * primary shard, so a read routed differently from its write misses documents.
         */
        LOGIC_SHARDING_AND_CUSTOM_ROUTING
    }

    private final String httpHostAddress;
    private final String namespace;
    @Getter
    private final StorageModuleElasticsearchConfig config;
    @Getter
    private final ElasticSearchClient client;
    private final StorageModels models;
    private final StorageEsDAO storageDAO;
    private final BatchProcessEsDAO batchDAO;
    private final Supplier<StorageEsInstaller> installers;
    /** One client for every request the ITs send the server outside OAP's client: refreshes, stats, shard probes. */
    private final HttpClient http = HttpClient.newHttpClient();

    private ElasticSearchQueryITSupport(final String httpHostAddress, final StorageModuleElasticsearchConfig config,
                                        final ElasticSearchClient client, final StorageModels models,
                                        final BatchProcessEsDAO batchDAO,
                                        final Supplier<StorageEsInstaller> installers) {
        this.httpHostAddress = httpHostAddress;
        this.namespace = config.getNamespace();
        this.config = config;
        this.client = client;
        this.models = models;
        this.storageDAO = new StorageEsDAO(client);
        this.batchDAO = batchDAO;
        this.installers = installers;
    }

    public static ElasticsearchContainer elasticsearch(final String version) {
        return new ElasticsearchContainer(
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch").withTag(version))
            .withEnv("xpack.security.enabled", "false")
            .withEnv("cluster.routing.allocation.disk.threshold_enabled", "false");
    }

    public static ElasticsearchContainer openSearch(final String version) {
        return new ElasticsearchContainer(
            DockerImageName.parse("opensearchproject/opensearch")
                           .withTag(version)
                           .asCompatibleSubstituteFor("docker.elastic.co/elasticsearch/elasticsearch-oss"))
            // `plugins.security.disabled=true` alone is not enough from 2.12 on: the image still installs the demo
            // security config, which refuses to run without OPENSEARCH_INITIAL_ADMIN_PASSWORD.
            .withEnv("DISABLE_INSTALL_DEMO_CONFIG", "true")
            .withEnv("DISABLE_SECURITY_PLUGIN", "true")
            // The Performance Analyzer agent's JVM can start beside the launcher's and make OpenSearch fail with
            // "Could not find or load main class Cannot" (opensearch-project/opensearch-build#3821).
            .withEnv("DISABLE_PERFORMANCE_ANALYZER_AGENT_CLI", "true")
            .withEnv("cluster.routing.allocation.disk.threshold_enabled", "false")
            .withStartupTimeout(Duration.ofMinutes(5));
    }

    /**
     * @param it the IT class connecting, which names the namespace its indices are written under
     */
    public static ElasticSearchQueryITSupport connect(final ElasticsearchContainer server, final Mode mode,
                                                      final Class<?> it) throws StorageException {
        return connect(server, mode, it, config -> { });
    }

    /**
     * @param it       the IT class connecting, which names the namespace its indices are written under
     * @param settings sets what an operator would set in the storage's {@code application.yml} section
     */
    public static ElasticSearchQueryITSupport connect(final ElasticsearchContainer server, final Mode mode,
                                                      final Class<?> it,
                                                      final Consumer<StorageModuleElasticsearchConfig> settings)
        throws StorageException {
        final StorageModuleElasticsearchConfig config = new StorageModuleElasticsearchConfig();
        config.setClusterNodes(server.getHttpHostAddress());
        if (mode == Mode.LOGIC_SHARDING_AND_CUSTOM_ROUTING) {
            config.setLogicSharding(true);
            config.setEnableCustomRouting(true);
            config.setIndexShardsNumber(2);
        }
        settings.accept(config);

        // What StorageModuleElasticsearchProvider#prepare() and #start() do with the config.
        config.setNamespace(it.getSimpleName().toLowerCase(Locale.ROOT));
        IndexNames.configure(config.getDayStep(), config.getSuperDatasetDayStep());
        final ModuleManager moduleManager = moduleManager();
        final ElasticSearchClient client = new ElasticSearchClient(
            moduleManager,
            config.getClusterNodes(), config.getProtocol(),
            config.getTrustStorePath(), config.getTrustStorePass(),
            config.getKeyStorePath(), config.getKeyStorePass(),
            config.getUser(), config.getPassword(),
            StorageModuleElasticsearchProvider.indexNameConverter(config.getNamespace()),
            config.getConnectTimeout(),
            config.getSocketTimeout(), config.getResponseTimeout(),
            config.getNumHttpClientThread()
        );
        final Supplier<StorageEsInstaller> installers = () -> {
            final StorageEsInstaller installer = new StorageEsInstaller(client, moduleManager, config);
            installer.setIndexRefreshInterval(new CoreModuleConfig().getPersistentPeriod() + 5);
            installer.start();
            return installer;
        };
        final BatchProcessEsDAO batchDAO = new BatchProcessEsDAO(
            client, config.getBulkActions(), config.getFlushInterval(), config.getConcurrentRequests(),
            config.getBatchOfBytes());
        IndexController.INSTANCE.setLogicSharding(config.isLogicSharding());
        IndexController.INSTANCE.setEnableCustomRouting(config.isEnableCustomRouting());
        client.connect();

        final StorageModels models = new StorageModels();
        models.addModelListener(installers.get());
        return new ElasticSearchQueryITSupport(
            server.getHttpHostAddress(), config, client, models, batchDAO, installers);
    }

    /**
     * Installs a record's index template the way OAP does at boot, from the record class's own annotations.
     */
    public Model installRecord(final Class<? extends Record> recordClass) throws StorageException {
        final Stream stream = recordClass.getAnnotation(Stream.class);
        return install(recordClass, stream.scopeId(), new Storage(stream.name(), false, DownSampling.Second));
    }

    /**
     * Installs the minute index template of a metrics class the way OAP does at boot.
     */
    public Model installMetrics(final Class<? extends Metrics> metricsClass) throws StorageException {
        final Stream stream = metricsClass.getAnnotation(Stream.class);
        final MetricsExtension extension = metricsClass.getAnnotation(MetricsExtension.class);
        final boolean timeRelativeID = extension == null || extension.timeRelativeID();
        return install(metricsClass, stream.scopeId(), new Storage(stream.name(), timeRelativeID, DownSampling.Minute));
    }

    /**
     * Installs the index template of a metrics class at one of its downsamplings, the way OAP does at boot.
     */
    public Model installMetrics(final Class<? extends Metrics> metricsClass,
                                final DownSampling downSampling) throws StorageException {
        final Stream stream = metricsClass.getAnnotation(Stream.class);
        final MetricsExtension extension = metricsClass.getAnnotation(MetricsExtension.class);
        final boolean timeRelativeID = extension == null || extension.timeRelativeID();
        return install(metricsClass, stream.scopeId(), new Storage(stream.name(), timeRelativeID, downSampling));
    }

    /**
     * Installs the minute index template of a MAL meter the way OAP does at boot. The meter's class is its function's
     * class, which names neither the metric nor its scope.
     */
    public Model installMeter(final Class<? extends Metrics> meterClass, final int scopeId,
                              final String name) throws StorageException {
        return install(meterClass, scopeId, new Storage(name, true, DownSampling.Minute));
    }

    /**
     * Installs the index template of a none-stream class (a task) the way {@link NoneStreamProcessor} does at boot.
     */
    public Model installNoneStream(final Class<? extends NoneStream> noneStreamClass) throws StorageException {
        final Stream stream = noneStreamClass.getAnnotation(Stream.class);
        return install(noneStreamClass, stream.scopeId(), new Storage(stream.name(), false, DownSampling.Minute));
    }

    /**
     * Installs a management model the way OAP does at boot, into the one index all management data shares.
     */
    public Model installManagement(final Class<? extends ManagementData> managementClass) throws StorageException {
        final Stream stream = managementClass.getAnnotation(Stream.class);
        return install(managementClass, stream.scopeId(), new Storage(stream.name(), false, DownSampling.None));
    }

    /**
     * Installs the model, then checks that a new installer, with nothing cached, finds it installed, as the next
     * boot's does: a stored template or index that reads back differently from what the installer writes is
     * rewritten on every boot.
     */
    private Model install(final Class<?> storageClass, final int scopeId,
                          final Storage storage) throws StorageException {
        final Model model;
        try (MockedStatic<DefaultScopeDefine> ignored = mockStatic(DefaultScopeDefine.class)) {
            model = models.add(storageClass, scopeId, storage, StorageManipulationOpt.schemaCreateIfAbsent());
        }
        final ModelInstaller.InstallInfo installed =
            installers.get().isExists(model, StorageManipulationOpt.schemaCreateIfAbsent());
        if (!installed.isAllExist()) {
            throw new IllegalStateException(
                "the installer does not find what it installed: " + installed.buildInstallInfoMsg());
        }
        return model;
    }

    /**
     * Writes the records in one bulk, through OAP's own record DAO.
     */
    public void writeRecords(final Model model, final StorageBuilder<? extends Record> builder,
                             final List<? extends Record> records) throws Exception {
        final IRecordDAO recordDAO = storageDAO.newRecordDao(builder);
        final List<PrepareRequest> requests = new ArrayList<>(records.size());
        for (final Record record : records) {
            requests.add(recordDAO.prepareBatchInsert(model, record));
        }
        flush(requests);
    }

    /**
     * Writes the metrics in one bulk, through OAP's own metrics DAO.
     */
    public void writeMetrics(final Model model, final StorageBuilder<? extends Metrics> builder,
                             final List<? extends Metrics> metrics) throws Exception {
        final IMetricsDAO metricsDAO = storageDAO.newMetricsDao(builder);
        final List<PrepareRequest> requests = new ArrayList<>(metrics.size());
        for (final Metrics metric : metrics) {
            requests.add(metricsDAO.prepareBatchInsert(model, metric, null));
        }
        flush(requests);
    }

    /**
     * Writes metrics again in one bulk, the way the metrics worker does when a stored metric changes: an update of
     * its document, in the index of the metric's time bucket, through OAP's own metrics DAO.
     */
    public void updateMetrics(final Model model, final StorageBuilder<? extends Metrics> builder,
                              final List<? extends Metrics> metrics) throws Exception {
        final IMetricsDAO metricsDAO = storageDAO.newMetricsDao(builder);
        final List<PrepareRequest> requests = new ArrayList<>(metrics.size());
        for (final Metrics metric : metrics) {
            requests.add(metricsDAO.prepareBatchUpdate(model, metric, null));
        }
        flush(requests);
    }

    /**
     * Writes a none-stream row (a task) the way {@link NoneStreamProcessor} does: at once, through OAP's own
     * none-stream DAO, outside the bulk, into the index of the row's time bucket.
     */
    public void writeNoneStream(final Model model, final StorageBuilder<? extends NoneStream> builder,
                                final NoneStream row) throws IOException, InterruptedException {
        storageDAO.newNoneStreamDao(builder).insert(model, row);
        refresh();
    }

    /**
     * Sends the requests the way the persistence timer does: queued into the bulk, which {@code endOfFlush} sends.
     */
    private void flush(final List<PrepareRequest> requests) throws Exception {
        final CompletableFuture<Void> written = batchDAO.flush(requests);
        batchDAO.endOfFlush();
        written.get(30, TimeUnit.SECONDS);
        refresh();
    }

    /**
     * Makes every write to this IT's indices searchable now, rather than at their next refresh.
     */
    public void refresh() throws IOException, InterruptedException {
        final HttpResponse<String> response = http.send(
            HttpRequest.newBuilder(URI.create("http://" + httpHostAddress + "/" + namespace + "_*/_refresh"))
                       .POST(HttpRequest.BodyPublishers.noBody())
                       .build(),
            HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("refresh " + namespace + "_*: " + response.statusCode() + " " + response.body());
        }
    }

    /**
     * The scroll contexts open on the server, from its node stats. A context left open is held until its scroll's
     * keep-alive ends, and a node refuses every new scroll past {@code search.max_open_scroll_context}.
     */
    public long openScrollContexts() throws IOException, InterruptedException {
        final HttpResponse<String> response = http.send(
            HttpRequest.newBuilder(URI.create("http://" + httpHostAddress + "/_nodes/stats/indices/search")).build(),
            HttpResponse.BodyHandlers.ofString());
        long open = 0;
        final JsonObject nodes = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("nodes");
        for (final Map.Entry<String, JsonElement> node : nodes.entrySet()) {
            open += node.getValue().getAsJsonObject()
                        .getAsJsonObject("indices")
                        .getAsJsonObject("search")
                        .get("scroll_current").getAsLong();
        }
        return open;
    }

    /**
     * The primary shard the server routes a record to, asked of the server ({@code _search_shards}) with the routing
     * OAP's record DAO writes the record with: the model's routing column under custom routing, the document id
     * otherwise. The record's index must exist.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public int shardOf(final Model model, final StorageBuilder<? extends Record> builder,
                       final Record record) throws IOException, InterruptedException {
        final ElasticSearchConverter.ToStorage columns = new ElasticSearchConverter.ToStorage(model.getName());
        ((StorageBuilder<Record>) (StorageBuilder) builder).entity2Storage(record, columns);
        final String routing = RoutingUtils.getRoutingValue(model, columns)
                                           .orElse(IndexController.INSTANCE.generateDocId(model, record.id().build()));
        final String index = namespace + "_" + IndexNames.writeIndex(model, record.getTimeBucket());
        final HttpResponse<String> response = http.send(
            HttpRequest.newBuilder(URI.create("http://" + httpHostAddress + "/" + index + "/_search_shards?routing="
                                                  + URLEncoder.encode(routing, StandardCharsets.UTF_8))).build(),
            HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("search shards of " + index + ": " + response.statusCode() + " " + response.body());
        }
        for (final JsonElement copy : JsonParser.parseString(response.body()).getAsJsonObject()
                                                .getAsJsonArray("shards").get(0).getAsJsonArray()) {
            if (copy.getAsJsonObject().get("primary").getAsBoolean()) {
                return copy.getAsJsonObject().get("shard").getAsInt();
            }
        }
        throw new IOException("no primary shard of " + index + " for " + routing + ": " + response.body());
    }

    /**
     * The shard of its index that holds a written record, as a search of its document id reports it.
     */
    public int shardHolding(final Model model, final Record record) throws IOException, InterruptedException {
        final String index = namespace + "_" + IndexNames.writeIndex(model, record.getTimeBucket());
        final JsonObject body = new JsonObject();
        body.addProperty("explain", true);
        body.addProperty("_source", false);
        final JsonObject ids = new JsonObject();
        final JsonArray values = new JsonArray();
        values.add(IndexController.INSTANCE.generateDocId(model, record.id().build()));
        ids.add("values", values);
        final JsonObject query = new JsonObject();
        query.add("ids", ids);
        body.add("query", query);
        final HttpResponse<String> response = http.send(
            HttpRequest.newBuilder(URI.create("http://" + httpHostAddress + "/" + index + "/_search"))
                       .header("Content-Type", "application/json")
                       .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                       .build(),
            HttpResponse.BodyHandlers.ofString());
        final JsonArray hits = JsonParser.parseString(response.body()).getAsJsonObject()
                                         .getAsJsonObject("hits").getAsJsonArray("hits");
        if (response.statusCode() != 200 || hits.size() != 1) {
            throw new IOException("search " + index + " by id: " + response.statusCode() + " " + response.body());
        }
        // "[index][shard]"
        final String shard = hits.get(0).getAsJsonObject().get("_shard").getAsString();
        return Integer.parseInt(shard.substring(shard.lastIndexOf('[') + 1, shard.length() - 1));
    }

    @Override
    public void close() {
        BatchProcessEsDAOITExt.close(batchDAO);
        client.shutdown();
    }

    private static ModuleManager moduleManager() {
        final ModuleManager moduleManager = mock(ModuleManager.class);
        final NoneTelemetryProvider telemetryProvider = mock(NoneTelemetryProvider.class);
        when(telemetryProvider.getService(MetricsCreator.class)).thenReturn(new MetricsCreatorNoop());
        final TelemetryModule telemetryModule = Mockito.spy(TelemetryModule.class);
        ReflectUtil.setInternalState(telemetryModule, "loadedProvider", telemetryProvider);
        when(moduleManager.find(TelemetryModule.NAME)).thenReturn(telemetryModule);
        return moduleManager;
    }
}
