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
        setDetailGroup(new org.apache.skywalking.oap.server.core.analysis.metrics.IntKeyLongValueHashMap(30));

        java.util.Iterator iterator = remoteData.getDataIntLongPairListList().iterator();
        while (iterator.hasNext()) {
            org.apache.skywalking.oap.server.core.remote.grpc.proto.IntKeyLongValuePair element = (org.apache.skywalking.oap.server.core.remote.grpc.proto.IntKeyLongValuePair)(iterator.next());
            super.getDetailGroup().put(new Integer(element.getKey()), new org.apache.skywalking.oap.server.core.analysis.metrics.IntKeyLongValue(element.getKey(), element.getValue()));
        }
    </#list>
}