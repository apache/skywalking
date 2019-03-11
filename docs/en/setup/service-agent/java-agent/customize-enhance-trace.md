## Support custom enhance 
Here is an optional plugin `apm-customize-enhance-plugin`

## Introduce
- The purpose of this plugin is to achieve Class enhancements to some extent through non-intrusive forms.
- The core idea is that there is no intrusion, so that no code related to this project appears in the project code.
- Implemented a custom enhancement of the custom languages, it looks more like [@Trace](Application-toolkit-trace.md) non-intrusive implementation,
internal tag records need to be used, ActiveSpan.tag to achieve, of course, it is to support static methods, 
you can use the custom languages to extend the operationName suffix, already log, and tag extension.                                                                                                      

## How to configure
Implementing enhancements to custom classes requires two steps.
 1. Set through the system environment variable, you need to add `skywalking.customize.enhance_file`.
 2. Configure your configuration file according to the demo below.
```xml
<?xml version="1.0" encoding="UTF-8"?>
<enhanced>
    <class class_name="test.apache.skywalking.testcase.customize.service.TestService1">
        <method method="staticMethod()" operation_name="/is_static_method" static="true"></method>
        <method method="staticMethod(java.lang.String,int.class,java.util.Map,java.util.List,[Ljava.lang.Object;)" operation_name="/is_static_method_args" static="true">
            <operation_name_suffix>arg[0]</operation_name_suffix>
            <operation_name_suffix>arg[1]</operation_name_suffix>
            <operation_name_suffix>arg[3].[0]</operation_name_suffix>
            <tag key="tag_1">arg[2].['k1']</tag>
            <tag key="tag_2">arg[4].[1]</tag>
            <log key="log_1">arg[4].[2]</log>
        </method>
        <method method="method()" static="false"></method>
        <method method="method(java.lang.String,int.class)" operation_name="/method_2" static="false">
            <operation_name_suffix>arg[0]</operation_name_suffix>
            <tag key="tag_1">arg[0]</tag>
            <log key="log_1">arg[1]</log>
        </method>
        <method method="method(test.apache.skywalking.testcase.customize.model.Model0,java.lang.String,int.class)" operation_name="/method_3" static="false">
            <operation_name_suffix>arg[0].id</operation_name_suffix>
            <operation_name_suffix>arg[0].model1.name</operation_name_suffix>
            <operation_name_suffix>arg[0].model1.getId()</operation_name_suffix>
            <tag key="tag_os">arg[0].os.[1]</tag>
            <log key="log_map">arg[0].getM().['k1']</log>
        </method>
    </class>
    <class class_name="test.apache.skywalking.testcase.customize.service.TestService2">
        <method method="staticMethod(java.lang.String,int.class)" operation_name="/is_2_static_method" static="true">
            <tag key="tag_2_1">arg[0]</tag>
            <log key="log_1_1">arg[1]</log>
        </method>
        <method method="method([Ljava.lang.Object;)" operation_name="/method_4" static="false">
            <tag key="tag_4_1">arg[0].[0]</tag>
        </method>
        <method method="method(java.util.List,int.class)" operation_name="/method_5" static="false">
            <tag key="tag_5_1">arg[0].[0]</tag>
            <log key="log_5_1">arg[1]</log>
        </method>
    </class>
</enhanced>
```
 3. The For a clearer usage, please see org.apache.skywalking.apm.plugin.customize.util.CustomizeExpressionTest.java


