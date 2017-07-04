package org.skywalking.apm.collector.worker.segment.mock;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.LinkedList;
import java.util.List;
import org.skywalking.apm.network.proto.TraceSegmentReference;

/**
 * @author pengys5
 */
public enum ReferencesFromJson {
    INSTANCE;

    private static final String parentTraceSegmentId = "parentTraceSegmentId";
    private static final String parentSpanId = "parentSpanId";
    private static final String parentApplicationId = "parentApplicationId";
    private static final String networkAddress = "networkAddress";
    private static final String networkAddressId = "networkAddressId";
    private static final String entryServiceName = "entryServiceName";
    private static final String entryServiceId = "entryServiceId";

    public List<TraceSegmentReference.Builder> build(JsonArray spanArray) {
        List<TraceSegmentReference.Builder> references = new LinkedList<>();
        for (int i = 0; i < spanArray.size(); i++) {
            JsonObject spanJson = spanArray.get(i).getAsJsonObject();
            TraceSegmentReference.Builder builder = TraceSegmentReference.newBuilder();
            buildRef(builder, spanJson);
            references.add(builder);
        }

        return references;
    }

    private void buildRef(TraceSegmentReference.Builder builder, JsonObject referenceJsonObj) {
        if (referenceJsonObj.has(parentTraceSegmentId)) {
            builder.setParentTraceSegmentId(referenceJsonObj.get(parentTraceSegmentId).getAsString());
        }
        if (referenceJsonObj.has(parentSpanId)) {
            builder.setParentSpanId(referenceJsonObj.get(parentSpanId).getAsInt());
        }
        if (referenceJsonObj.has(parentApplicationId)) {
            builder.setParentApplicationId(referenceJsonObj.get(parentApplicationId).getAsInt());
        }
        if (referenceJsonObj.has(networkAddress)) {
            builder.setNetworkAddress(referenceJsonObj.get(networkAddress).getAsString());
        }
        if (referenceJsonObj.has(networkAddressId)) {
            builder.setNetworkAddressId(referenceJsonObj.get(networkAddressId).getAsInt());
        }
        if (referenceJsonObj.has(entryServiceName)) {
            builder.setEntryServiceName(referenceJsonObj.get(entryServiceName).getAsString());
        }
        if (referenceJsonObj.has(entryServiceId)) {
            builder.setEntryServiceId(referenceJsonObj.get(entryServiceId).getAsInt());
        }
    }
}
