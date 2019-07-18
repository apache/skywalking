public org.apache.skywalking.oap.server.core.storage.StorageData map2Data(java.util.Map dbMap) {
    org.apache.skywalking.oal.rt.metrics.${metricsName}Metrics metrics = new org.apache.skywalking.oal.rt.metrics.${metricsName}Metrics();
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