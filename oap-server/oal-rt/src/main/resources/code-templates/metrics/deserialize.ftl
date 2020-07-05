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

<#list serializeFields.objectFields as field>
    ${field.setter}(new ${field.fieldType}(remoteData.getDataObjectStrings(${field?index})));
</#list>

}