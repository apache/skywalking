public org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData.Builder serialize() {
    org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData.Builder remoteBuilder = org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData.newBuilder();
    <#list serializeFields.stringFields as field>
        remoteBuilder.addDataStrings(${field.getter}());
    </#list>

    <#list serializeFields.longFields as field>
        remoteBuilder.addDataLongs(${field.getter}());
    </#list>

    <#list serializeFields.doubleFields as field>
        remoteBuilder.addDataDoubles(${field.getter}());
    </#list>

    <#list serializeFields.intFields as field>
        remoteBuilder.addDataIntegers(${field.getter}());
    </#list>
    <#list serializeFields.intLongValuePairListFields as field>
        java.util.Collection collection = super.getDetailGroup().values();
        for (Object value : collection) {
        org.apache.skywalking.oap.server.core.analysis.metrics.IntKeyLongValue intKeyLongValue = (org.apache.skywalking.oap.server.core.analysis.metrics.IntKeyLongValue)value;
            remoteBuilder.addDataIntLongPairList(intKeyLongValue.serialize());
        }
    </#list>

    return remoteBuilder;
}