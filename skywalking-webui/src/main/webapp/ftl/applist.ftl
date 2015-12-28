<#import "./lib/ai.cloud/common.ftl" as common>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta http-equiv="content-type" content="text/html; charset=UTF-8">
    <meta charset="utf-8">
    <title>Sky Walking</title>
    <meta name="generator" content="Bootply"/>
    <meta name="viewport"
          content="width=device-width, initial-scale=1, maximum-scale=1">
    <link href="${base}/css/bootstrap.min.css" rel="stylesheet">
    <!--[if lt IE 9]>
    <script src="//html5shim.googlecode.com/svn/trunk/html5.js"></script>
    <![endif]-->
    <link href="${base}/css/login.css" rel="stylesheet"/>
    <!-- script references -->
<@common.importJavaScript />
</head>
<body>
<div class="container">
    <table class="table table-condensed" style="color: white;">
        <caption>
            <button id="crtApp" type="button" class="btn btn-warning" href="javascript:void(0);">创建应用</button>
            &nbsp;
            <button id="crtAlarm" type="button" class="btn btn-warning" href="javascript:void(0);">默认告警规则</button>
            &nbsp;
            <button type="button" class="btn btn-success" href="javascript:void(0);"
                    onclick="window.location.reload(); return false;">
                刷新
            </button>
        </caption>
        <thead>
        <tr>
            <th style="width:5%">序号</th>
            <th style="width:60%">应用名称</th>
            <th style="width:20%">操作</th>
        </tr>
        </thead>
    <#if applist??>
        <tbody>
            <#list applist as appInfo>
            <tr>
                <th scope="row">${appInfo_index + 1}</th>
                <td>${appInfo.appCode!}</td>
                <td>
                    <button name="conf" appId="${appInfo.appId!}" appCode="${appInfo.appCode!}" type="button"
                            class="btn btn-default btn-xs">配置告警规则
                    </button>
                    <button name="exportAuthInfo" appId="${appInfo.appId!}" appCode="${appInfo.appCode!}" type="button"
                            class="btn btn-default btn-xs">生成授权文件
                    </button>
                    <button name="del" appId="${appInfo.appId!}" type="button" class="btn btn-default btn-xs">删除
                    </button>
                </td>
            </tr>
            </#list>
        </tbody>
    </#if>
    </table>
</div>
<div id="authFiledownLoad">
</div>
<script type="text/javascript">
    $().ready(function () {
        $("#crtApp").bind("click", function () {
            jQuery.ajax({
                type: 'POST',
                url: "${base}/app/create",
                success: function (data) {
                    bootbox.dialog({
                        message: data,
                        title: "创建应用",
                        size: "large",
                        locale: "zh_CN",
                        buttons: {
                            "createBtn": {
                                label: '创建应用',
                                callback: function () {
                                    if ($("#appCode").val() == '') {
                                        alert("请输入应用名称");
                                        return false;
                                    }
                                    var urlStr = '${base}/appinfo/create';
                                    $.ajax({
                                        type: 'POST',
                                        url: urlStr,
                                        contentType: "application/json",
                                        data: "{'appCode':'" + $("#appCode").val() + "'}",
                                        dataType: 'json',
                                        async: false,
                                        success: function (data) {
                                            console.log(data);
                                            var result = data.result;
                                            if (result == 'OK') {
                                                alert(data.msg);
                                                $("#createAppDiv").hide();
                                                window.location.reload();
                                            } else {
                                                alert(data.msg);
                                            }
                                        },
                                        error: function (xhr, type) {
                                            alert("操作失败");
                                        }
                                    });
                                }
                            },
                            "cancelBtn": {
                                label: '取消创建',
                                callback: function () {
                                    bootbox.hideAll();
                                }
                            }
                        }
                    });
                }
            });
        });

        $("button[name='del']").each(function () {
            $(this).bind("click", function () {
                var appId = $(this).attr("appId");
                if (appId < 0) {
                    alert("请选择应用");
                    return false;
                }
                var urlStr = '${base}/appinfo/delete/' + appId;
                $.ajax({
                    type: 'POST',
                    url: urlStr,
                    contentType: "application/json",
                    data: {},
                    dataType: 'json',
                    async: false,
                    success: function (data) {
                        console.log(data);
                        var result = data.result;
                        if (result == 'OK') {
                            alert(data.msg);
                            window.location.reload();
                        } else {
                            alert(data.msg);
                        }
                    },
                    error: function (xhr, type) {
                        alert("操作失败");
                    }
                });
            });
        });
        $("#crtAlarm").bind("click", function () {
            configAlarmRule("default", "所有应用", 1);
        });

        function configAlarmRule(appId, appCode, isGlobal) {
            jQuery.ajax({
                type: 'POST',
                url: "${base}/alarm-rule/list/" + appId,
                data: {
                    "appCode": function () {
                        return appCode;
                    },
                    "isGlobal": function () {
                        return isGlobal;
                    }
                },
                success: function (data) {
                    bootbox.dialog({
                        message: data,
                        title: "创建告警规则",
                        size: "large",
                        locale: "zh_CN",
                        buttons: {
                            "createBtn": {
                                label: '创建规则',
                                callback: function () {
                                    var ruleId = $("#ruleId").val();
                                    var appId = $("#appId").val();//可空
                                    var period = $("#period").val();//不可空
                                    if (period == null || period.length < 1) {
                                        alert("告警频率不能为空");
                                        return false;
                                    }
                                    var isGlobal = $("#isGlobal").val();//不可空
                                    if (isGlobal == null || isGlobal.length < 1) {
                                        alert("规则标识不能为空");
                                        return false;
                                    } else {
                                        if (isGlobal == 0) {
                                            if (appId == null || appId.length < 1) {
                                                alert("应用标识不能为空");
                                                return false;
                                            }
                                        }
                                    }

                                    var todoType = $("#todoType").val();//不可空
                                    if (todoType == null || todoType.length < 1) {
                                        alert("告警操作不能为空");
                                        return false;
                                    }
                                    var todoContent = "";
                                    var mailTo = $("#mailTo").val();
                                    var mailCc = $("#mailCc").val();
                                    var ruleId = $("#ruleId").val();
                                    var jsonData = "";
                                    if (ruleId > 0) {
                                        //调用修改规则
                                        if (ruleId == null || ruleId.length < 0) {
                                            alert("告警规则不能为空");
                                            return false;
                                        }
                                        var urlStr = '${base}/alarmRule/modify';
                                        if (todoType == 0) {
                                            jsonData = "{ruleId:'" + ruleId + "',appId:'" + appId + "',period:'" + period + "',isGlobal:'" + isGlobal + "',todoType:'" + todoType + "',mailTo:'" + mailTo + "',mailCc:'" + mailCc + "'}";
                                        } else if (todoType == 1) {
                                            jsonData = "{ruleId:'" + ruleId + "',appId:'" + appId + "',period:'" + period + "',isGlobal:'" + isGlobal + "',todoType:'" + todoType + "',urlCall:'" + urlCall + "'}";
                                        } else {
                                            alert("请选择正确的告警操作");
                                            return false;
                                        }
                                        //alert(jsonData);
                                        $.ajax({
                                            type: 'POST',
                                            url: urlStr,
                                            contentType: "application/json",
                                            data: jsonData,
                                            dataType: 'json',
                                            async: false,
                                            success: function (data) {
                                                if (data.result == "OK") {
                                                    alert(data.msg);
                                                    window.location.reload();
                                                } else {
                                                    alert(data.msg);
                                                }
                                            },
                                            error: function (xhr, type) {
                                                alert("操作失败");
                                            }
                                        });
                                    } else {
                                        //调用创建规则
                                        var urlStr = '${base}/alarmRule/create';
                                        if (todoType == 0) {
                                            jsonData = "{appId:'" + appId + "',period:'" + period + "',isGlobal:'" + isGlobal + "',todoType:'" + todoType + "',mailTo:'" + mailTo + "',mailCc:'" + mailCc + "'}";
                                        } else if (todoType == 1) {
                                            jsonData = "{appId:'" + appId + "',period:'" + period + "',isGlobal:'" + isGlobal + "',todoType:'" + todoType + "',urlCall:'" + urlCall + "'}";
                                        } else {
                                            alert("请选择正确的告警操作");
                                            return false;
                                        }
                                        alert(jsonData);
                                        $.ajax({
                                            type: 'POST',
                                            url: urlStr,
                                            contentType: "application/json",
                                            data: jsonData,
                                            dataType: 'json',
                                            async: false,
                                            success: function (data) {
                                                if (data.result == "OK") {
                                                    alert(data.msg);
                                                    window.location.reload();
                                                } else {
                                                    alert(data.msg);
                                                }
                                            },
                                            error: function (xhr, type) {
                                                alert("操作失败");
                                            }
                                        });
                                    }
                                }
                            },
                            "cancelBtn": {
                                label: '取消创建',
                                callback: function () {
                                    bootbox.hideAll();
                                }
                            }
                        }
                    });
                }
            });
        }

        $("button[name='conf']").each(function () {
            $(this).bind("click", function () {
                var appId = $(this).attr("appId");
                var appCode = $(this).attr("appCode");
                configAlarmRule(appId, appCode, 0);
            });
        });


        $("button[name='exportAuthInfo']").each(function () {
            $(this).bind("click", function () {
                var authAppCode = $(this).attr("appCode");
                jQuery.ajax({
                    type: 'POST',
                    url: "${base}/download/" + authAppCode,
                    success: function (data) {
                        bootbox.dialog({
                            message: data,
                            title: "下载授权文件",
                            size: "large",
                            locale: "zh_CN",
                            buttons: {
                                "downloadBtn": {
                                    label: '确认下载',
                                    callback: function () {
                                        var appCode = $("#authAppCode").val();
                                        if (appCode == null) {
                                            alert("没有发现应用编码.");
                                            return;
                                        }
                                        var form = $("<form>");//定义一个form表单
                                        form.attr("style", "display:none");
                                        form.attr("method", "post");
                                        form.attr("action", "${base}/exportAuth/" + appCode);
                                        var exportData = $("<input>");
                                        exportData.attr("type", "hidden");
                                        exportData.attr("name", "exportData");
                                        exportData.attr("value", (new Date()).getMilliseconds());
                                        var exclusiveException = $("<input>");
                                        exclusiveException.attr("type", "hidden");
                                        exclusiveException.attr("name", "exclusiveException");
                                        exclusiveException.attr("value", $("#exclusiveException").val());
                                        var authType = $("<input>");
                                        authType.attr("type", "hidden");
                                        authType.attr("name", "authType");
                                        authType.attr("value", $("#authType").val());
                                        $("#authFiledownLoad").append(form);//将表单放置在web中
                                        form.append(exportData);
                                        form.append(exclusiveException);
                                        form.append(authType);

                                        form.submit();//表单提交
                                    }
                                },
                                "cancelBtn": {
                                    label: '取消下载',
                                    callback: function () {
                                        bootbox.hideAll();
                                    }
                                }
                            }
                        });
                    }
                });
            });
        });

        $("#export").bind("click", function () {

        });

        $("#cannelExport").bind("click", function () {
            $("#authInfo").hide();
        });

    });
</script>
</body>
</html>