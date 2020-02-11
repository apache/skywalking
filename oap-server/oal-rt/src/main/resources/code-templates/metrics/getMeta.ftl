public org.apache.skywalking.oap.server.core.analysis.metrics.MetricsMetaInfo getMeta() {
return new org.apache.skywalking.oap.server.core.analysis.metrics.MetricsMetaInfo("${varName}", ${sourceScopeId}<#if (fieldsFromSource?size>0) ><#list fieldsFromSource as field><#if field.isID()>, ${field.fieldName}</#if></#list></#if>);
}