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
        <method method="staticMethod()" operation_name="/test_static" static="true"></method>
        <method method="method(java.lang.String,int.class)" operation_name="/test" static="false">
            <operation_name_suffix>arg[0]</operation_name_suffix>
            <tag key="str">arg[0]</tag>
            <log key="num">arg[1]</log>
        </method>
    </class>
</enhanced>
```
 3. The For a clearer usage, please see org.apache.skywalking.apm.plugin.customize.util.CustomizeExpressionTest.java


