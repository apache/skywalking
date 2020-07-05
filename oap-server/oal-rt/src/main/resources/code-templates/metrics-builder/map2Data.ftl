public org.apache.skywalking.oap.server.core.storage.StorageData map2Data(java.util.Map dbMap) {
${metricsClassPackage}${metricsName}Metrics metrics = new ${metricsClassPackage}${metricsName}Metrics();
<#list fieldsFromSource as field>
    <#if field.typeName == "long" || field.typeName == "int" || field.typeName == "double" || field.typeName == "float">
        metrics.${field.fieldSetter}(((Number)dbMap.get("${field.columnName}")).${field.typeName}Value());
    <#elseif field.typeName == "java.lang.String">
        metrics.${field.fieldSetter}((String)dbMap.get("${field.columnName}"));
    <#else>
        metrics.${field.fieldSetter}(new ${field.typeName}((String)dbMap.get("${field.columnName}")));
    </#if>
</#list>
<#list persistentFields as field>
    <#if field.typeName == "long" || field.typeName == "int" || field.typeName == "double" || field.typeName == "float">
        metrics.${field.fieldSetter}(((Number)dbMap.get("${field.columnName}")).${field.typeName}Value());
    <#elseif field.typeName == "java.lang.String">
        metrics.${field.fieldSetter}((String)dbMap.get("${field.columnName}"));
    <#else>
        metrics.${field.fieldSetter}(new ${field.typeName}((String)dbMap.get("${field.columnName}")));
    </#if>
</#list>
return metrics;
}