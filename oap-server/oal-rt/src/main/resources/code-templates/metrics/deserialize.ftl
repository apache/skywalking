public void deserialize(org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData remoteData) {
    <#list serializeFields.stringFields as field>
        ${field.setter}(remoteData.getDataStrings(${field?index}));
    </#list>

    <#list serializeFields.longFields as field>
        ${field.setter}(remoteData.getDataLongs(${field?index}));
    </#list>

    <#list serializeFields.doubleFields as field>
        ${field.setter}(remoteData.getDataDoubles(${field?index}));
    </#list>

    <#list serializeFields.intFields as field>
        ${field.setter}(remoteData.getDataIntegers(${field?index}));
    </#list>

    <#list serializeFields.intLongValuePairListFields as field>
        setDetailGroup(new org.apache.skywalking.oap.server.core.analysis.metrics.IntKeyLongValueArray(30));

        Iterator<rg.apache.skywalking.oap.server.core.remote.grpc.proto.IntKeyLongValuePair> iterator = remoteData.getDataIntLongPairListList().iterator();
        while (iterator.hasNext()) {
            IntKeyLongValuePair element = iterator.next();
            getDetailGroup().add(new IntKeyLongValue(element.getKey(), element.getValue()));
        }
    </#list>
}