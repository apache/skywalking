protected org.apache.skywalking.oap.server.core.storage.StorageID id0() {
org.apache.skywalking.oap.server.core.storage.StorageID id = new org.apache.skywalking.oap.server.core.storage.StorageID().append(TIME_BUCKET, getTimeBucket());
<#list fieldsFromSource as sourceField>
    <#if sourceField.isID()>
        id.append("${sourceField.columnName}", ${sourceField.fieldName});
    </#if>
</#list>
return id;
}
