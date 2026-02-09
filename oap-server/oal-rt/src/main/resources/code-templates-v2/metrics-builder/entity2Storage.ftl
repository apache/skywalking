public void entity2Storage(org.apache.skywalking.oap.server.core.storage.StorageData input, org.apache.skywalking.oap.server.core.storage.type.Convert2Storage converter) {
${metricsClassPackage}${metricsName}Metrics storageData = (${metricsClassPackage}${metricsName}Metrics)input;
<#list fieldsFromSource as field>
    <#if field.typeName == "long">
        converter.accept("${field.columnName}", new Long(storageData.${field.fieldGetter}()));
    <#elseif field.typeName == "int">
        converter.accept("${field.columnName}", new Integer(storageData.${field.fieldGetter}()));
    <#elseif field.typeName == "double">
        converter.accept("${field.columnName}", new Double(storageData.${field.fieldGetter}()));
    <#elseif field.typeName == "float">
        converter.accept("${field.columnName}", new Float(storageData.${field.fieldGetter}()));
    <#else>
        converter.accept("${field.columnName}", storageData.${field.fieldGetter}());
    </#if>
</#list>
<#list persistentFields as field>
    <#assign fieldTypeName = field.type.name>
    <#assign fieldGetter = "get" + field.fieldName?cap_first>
    <#if fieldTypeName == "long">
        converter.accept("${field.columnName}", new Long(storageData.${fieldGetter}()));
    <#elseif fieldTypeName == "int">
        converter.accept("${field.columnName}", new Integer(storageData.${fieldGetter}()));
    <#elseif fieldTypeName == "double">
        converter.accept("${field.columnName}", new Double(storageData.${fieldGetter}()));
    <#elseif fieldTypeName == "float">
        converter.accept("${field.columnName}", new Float(storageData.${fieldGetter}()));
    <#else>
        converter.accept("${field.columnName}", storageData.${fieldGetter}());
    </#if>
</#list>
}
