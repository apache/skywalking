public void dispatch(org.apache.skywalking.oap.server.core.source.Source source) {
    org.apache.skywalking.oap.server.core.source.${source} _source = (org.apache.skywalking.oap.server.core.source.${source})source;
    <#list metrics as metrics>
        do${metrics.metricsName}(_source);
    </#list>
}