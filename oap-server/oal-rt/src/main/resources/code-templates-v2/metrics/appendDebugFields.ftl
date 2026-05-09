protected void appendDebugFields(com.google.gson.JsonObject obj) {
super.appendDebugFields(obj);
<#list fieldsFromSource as field>
    <#if field.typeName == "long">
        obj.addProperty("${field.columnName}", java.lang.Long.valueOf(${field.fieldGetter}()));
    <#elseif field.typeName == "int">
        obj.addProperty("${field.columnName}", java.lang.Integer.valueOf(${field.fieldGetter}()));
    <#elseif field.typeName == "double">
        obj.addProperty("${field.columnName}", java.lang.Double.valueOf(${field.fieldGetter}()));
    <#elseif field.typeName == "float">
        obj.addProperty("${field.columnName}", java.lang.Float.valueOf(${field.fieldGetter}()));
    <#elseif field.typeName == "boolean">
        obj.addProperty("${field.columnName}", java.lang.Boolean.valueOf(${field.fieldGetter}()));
    <#else>
        obj.addProperty("${field.columnName}", ${field.fieldGetter}() == null ? null : ${field.fieldGetter}().toString());
    </#if>
</#list>
}
