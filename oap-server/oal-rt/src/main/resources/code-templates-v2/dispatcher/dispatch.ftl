public void dispatch(org.apache.skywalking.oap.server.core.source.ISource source) {
${sourcePackage}${sourceName} _source = (${sourcePackage}${sourceName})source;
<#list metrics as metric>
    do${metric.metricsName}(_source);
</#list>
}
