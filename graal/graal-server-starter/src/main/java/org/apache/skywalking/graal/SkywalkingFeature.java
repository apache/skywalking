package org.apache.skywalking.graal;

import org.apache.kafka.clients.consumer.RangeAssignor;
import org.apache.skywalking.library.elasticsearch.response.Document;
import org.apache.skywalking.library.elasticsearch.response.Documents;
import org.apache.skywalking.library.elasticsearch.response.Index;
import org.apache.skywalking.library.elasticsearch.response.IndexTemplate;
import org.apache.skywalking.library.elasticsearch.response.IndexTemplates;
import org.apache.skywalking.library.elasticsearch.response.Mappings;
import org.apache.skywalking.library.elasticsearch.response.NodeInfo;
import org.apache.skywalking.library.elasticsearch.response.search.SearchHit;
import org.apache.skywalking.library.elasticsearch.response.search.SearchHits;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
import org.apache.skywalking.oap.server.analyzer.agent.kafka.module.KafkaFetcherConfig;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageConfig;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.StorageModuleElasticsearchConfig;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import java.util.Arrays;
import java.util.List;

public class SkywalkingFeature implements Feature {
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        registerAllOfClazz(BanyanDBStorageConfig.class);
        registerAllOfClazz(StorageModuleElasticsearchConfig.class);

        //kafka
        registerAllOfClazz(KafkaFetcherConfig.class);
        registerAllOfClazz(RangeAssignor.class);

        //e2e
        registerAllOfClazz(Duration.class);

        //es
        registerES();
    }

    private void registerES() {
        registerClasses(Arrays.asList(
                SearchHit.class,
                SearchHits.class,
                SearchHits.TotalDeserializer.class,
                Mappings.Source.class,
                NodeInfo.Version.class,
                SearchResponse.class,
                Document.class,
                Documents.class,
                Index.class,
                IndexTemplate.class,
                IndexTemplates.class,
                Mappings.class,
                NodeInfo.class
        ));
    }

    private void registerClasses(List<Class> classes) {
        classes.forEach(this::registerAllOfClazz);
    }

    private void registerAllOfClazz(Class targetClass) {

        RuntimeReflection.register(targetClass);

        RuntimeReflection.register(targetClass.getDeclaredMethods());

        RuntimeReflection.register(targetClass.getDeclaredFields());
        for (Class declaredClass : targetClass.getDeclaredClasses()) {
            RuntimeReflection.register(declaredClass);
        }
        RuntimeReflection.register(targetClass.getDeclaredConstructors());
    }
}
