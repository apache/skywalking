public void deserialize(org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData remoteData) {
<#if serializeFields.stringFields?? && serializeFields.stringFields?size gt 0>
    <#list serializeFields.stringFields as field>
        ${field.setter}(remoteData.getDataStrings(${field_index}));
    </#list>
</#if>
<#if serializeFields.longFields?? && serializeFields.longFields?size gt 0>
    <#list serializeFields.longFields as field>
        <#if field_index == 0>
            setTimeBucket(remoteData.getDataLongs(${field_index}));
        <#else>
            ${field.setter}(remoteData.getDataLongs(${field_index}));
        </#if>
    </#list>
</#if>
<#if serializeFields.doubleFields?? && serializeFields.doubleFields?size gt 0>
    <#list serializeFields.doubleFields as field>
        ${field.setter}(remoteData.getDataDoubles(${field_index}));
    </#list>
</#if>
<#if serializeFields.intFields?? && serializeFields.intFields?size gt 0>
    <#list serializeFields.intFields as field>
        ${field.setter}(remoteData.getDataIntegers(${field_index}));
    </#list>
</#if>
<#if serializeFields.objectFields?? && serializeFields.objectFields?size gt 0>
    <#list serializeFields.objectFields as field>
        ${field.setter}(new ${field.fieldType}(remoteData.getDataObjectStrings(${field_index})));
    </#list>
</#if>
}
