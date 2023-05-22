public org.apache.skywalking.oap.server.core.analysis.metrics.MetricsMetaInfo getMeta() {
return new org.apache.skywalking.oap.server.core.analysis.metrics.MetricsMetaInfo("${varName}", ${from.sourceScopeId?c}<#if (fieldsFromSource?size>0) >, id()</#if>);
}