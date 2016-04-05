<#import "../../common/commons.ftl" as common>
<!DOCTYPE html>
<html lang="zh-CN">

<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"/>
<@common.importResources />
    <title>Modify application configuration</title>
    <link href="${_base}/bower_components/bootstrap-toggle/css/bootstrap-toggle.min.css" rel="stylesheet">
    <script src="${_base}/bower_components/bootstrap-toggle/js/bootstrap-toggle.min.js"></script>
</head>

<body style="padding-top:80px">
<@common.navbar/>
<div class="container">
    <div class="row" style="display: none;" id="successMessageAlter">
        <div class="col-md-6 col-md-offset-2">
            <div class="alert alert-success alert-dismissible" role="alert">
                <button type="button" class="close" data-dismiss="alert" aria-label="Close"><span
                        aria-hidden="true">&times;</span></button>
                <strong>Congratuate!</strong>&nbsp;You had craete application success,
                you may <a href="${_base}/usr/applications/list">
                <ins>see all application</ins>
            </a> or <a href="${_base}/usr/applications/add">
                <ins>Create another application</ins>
            </a>.
            </div>
        </div>
    </div>
    <div class="row" id="errorMessageAlter" style="display: none">
        <div class="col-md-6 col-md-offset-2">
            <div class="alert alert-danger alert-dismissible" role="alert">
                <button type="button" class="close" data-dismiss="alert" aria-label="Close"><span
                        aria-hidden="true">&times;</span></button>
                <strong>Error!</strong><span id="errormessage"></span>.
            </div>
        </div>
    </div>
    <div class="row">
        <div class="col-md-6 col-md-offset-2">
            <div class="row">
                <form class="form-horizontal">
                    <div class="form-group">
                        <input type="hidden" id="applicationId" name="applicationId" value="${applicationId}"/>
                        <label for="appCode" class="col-sm-3 control-label">应用编码:</label>
                        <div class="col-sm-9">
                            <span style="padding-top: 10%"><p id="appCode"></p></span>
                        </div>
                    </div>
                    <div class="form-group">
                        <label for="inputPassword3" class="col-sm-3 control-label">应用描述:</label>
                        <div class="col-sm-9">
                            <input type="text" class="form-control" id="appDesc" placeholder="Application Description">
                        </div>
                    </div>
                    <div class="form-group">
                        <label for="isGlobalConfig" class="col-sm-3 control-label">使用全局配置:</label>
                        <div class="col-sm-3">
                            <input data-toggle="toggle" type="checkbox" id="isGlobalConfig"/>
                        </div>
                        <div class="col-sm-6" style="margin-top: 1%;display: none" id="isModifyGlobalConfig">
                            <input type="checkbox" id="isUpdateGlobalConfig"/> Update the global config
                        </div>
                    </div>
                    <p id="defaultConfigID" value="" style="display: none"></p>
                    <div class="panel panel-default" id="sysConfigParam">
                        <div class="panel-heading">
                            告警配置
                        </div>
                        <div class="panel-body">
                            <div class="form-group">
                                <label for="inputPassword3" class="col-sm-3 control-label">告警周期:</label>
                                <div class="col-sm-9">
                                    <input type="text" class="form-control" id="period" name="period"
                                           placeholder="10(M)"/>
                                </div>
                            </div>
                            <div class="form-group">
                                <label for="inputPassword3" class="col-sm-3 control-label">告警类型:</label>
                                <div class="col-sm-9">
                                    <span>发送邮件</span>
                                </div>
                            </div>
                            <div class="form-group">
                                <label for="inputPassword3" class="col-sm-3 control-label">收件人地址:</label>
                                <div class="col-sm-9">
                                    <input type="text-are" class="form-control" id="mailTo" name="mailTo"
                                           placeholder="Application Description">
                                </div>
                            </div>
                            <div class="form-group">
                                <label for="inputPassword3" class="col-sm-3 control-label">抄送人地址:</label>
                                <div class="col-sm-9">
                                    <input type="text" class="form-control" id="mailCc" name="mailCc"
                                           placeholder="Application Description">
                                </div>
                            </div>
                        </div>
                    </div>
                    <div class="form-group">
                        <div class="col-sm-offset-4 col-sm-1">
                            <button type="button" id="updateBtn" data-loading-text="Loading..." class="btn btn-primary"
                                    autocomplete="off">
                                <strong>
                                    <ins>Update</ins>
                                </strong>
                            </button>
                        </div>
                        <div class="col-sm-offset-1 col-sm-1">
                            <button type="button" class="btn btn-default"><strong>
                                <ins>Cancel</ins>
                            </strong></button>
                        </div>
                    </div>
                </form>
            </div>
        </div>
    </div>
    <script>
        $(document).ready(function () {
            $("#isGlobalConfig").change(function () {
                if ($(this).prop("checked")) {
                    //替换成
                    loadDefaultAlarmRule($("#applicationId").val());
                    $("#sysConfigParam input").each(function () {
                        $(this).prop("disabled", true);
                    });
                    $("#isModifyGlobalConfig").show();
                } else {
                    //
                    loadAlarmRule($("#applicationId").val());
                    $("#sysConfigParam input").each(function () {
                        $(this).prop("disabled", false);
                    });
                    $("#isUpdateGlobalConfig").prop("checked", false);
                    $("#isModifyGlobalConfig").hide();
                }
            });

            $("#isUpdateGlobalConfig").change(function () {
                if ($(this).prop("checked")) {
                    $("#sysConfigParam input").each(function () {
                        $(this).prop("disabled", "");
                    });
                } else {
                    $("#sysConfigParam input").each(function () {
                        $(this).prop("disabled", "disabled");
                    });
                }
            });

            $("#updateBtn").click(function () {
                var url = "${_base}/usr/applications/update/" + $("#applicationId").val();
                $.ajax({
                    type: 'POST',
                    url: url,
                    data: {
                        "appInfo": function () {
                            var application = {};
                            application.appDesc = $("#appDesc").val();
                            application.isGlobalConfig = $("#isGlobalConfig").prop("checked");
                            application.isUpdateGlobalConfig = $("#isUpdateGlobalConfig").prop("checked");

                            var configArgs = {};
                            configArgs.period = $("#period").val();

                            var mailInfo = {};
                            mailInfo.mailTo = $("#mailTo").val().split(",");
                            mailInfo.mailCc = $("#mailCc").val().split(",");

                            configArgs.mailInfo = mailInfo;
                            application.configArgs = configArgs;

                            return JSON.stringify(application);
                        }
                    },
                    dataType: 'json',
                    success: function (data) {
                        if (data.code == 200) {
                            $("#successMessageAlter").show();
                        } else {
                            $("#errormessage").text(data.message);
                            $("#errorMessageAlter").show();
                        }
                    },
                    error: function () {
                        $("#errormessage").text("Fatal error");
                        $("#errorMessageAlter").show();
                    }
                });
            });
            loadApplicationInfo($("#applicationId").val());
        });

        function loadAlarmRule(applicationId) {
            var url = "${_base}/usr/applications/alarm-rule/load/" + applicationId;
            $.ajax({
                type: 'POST',
                url: url,
                dataType: 'json',
                success: function (data) {
                    if (data.code == 200) {
                        var alarmRules = jQuery.parseJSON(data.result);
                        if (alarmRules.isGlobalAlarm) {
                            $("#period").val(alarmRules.globalAlarmRule.configArgs.period);
                            $("#mailTo").val(alarmRules.globalAlarmRule.configArgs.mailInfo.mailTo);
                            $("#mailCc").val(alarmRules.globalAlarmRule.configArgs.mailInfo.mailCc);
                        } else {
                            $("#period").val(alarmRules.selfAlarmRule.configArgs.period);
                            $("#mailTo").val(alarmRules.selfAlarmRule.configArgs.mailInfo.mailTo);
                            $("#mailCc").val(alarmRules.selfAlarmRule.configArgs.mailInfo.mailCc);
                        }
                    }
                },
                error: function () {
                    $("#errormessage").text("Fatal error");
                    $("#errorMessageAlter").show();
                }
            });
        }


        function loadApplicationInfo(applicationId) {
            var url = "${_base}/usr/applications/load/" + applicationId;
            $.ajax({
                type: 'POST',
                url: url,
                dataType: 'json',
                success: function (data) {
                    if (data.code == 200) {
                        var application = jQuery.parseJSON(data.result);
                        $("#appCode").text(application.appCode);
                        $("#appDesc").val(application.appDecs);
                        if (application.isGlobalAlarmRule) {
                            $("#isGlobalConfig").prop("checked", true).change();
                            $("#isModifyGlobalConfig").show();
                        } else {
                            $("#isGlobalConfig").prop("checked", false).change();
                            $("#isModifyGlobalConfig").hide();
                        }

                        if (!application.hasGlobalAlarmRule) {
                            $("#isGlobalConfig").prop("checked", false).change();
                            $("#isGlobalConfig").prop("disabled", "disabled");
                        }
                    }
                },
                error: function () {
                    $("#errormessage").text("Fatal error");
                    $("#errorMessageAlter").show();
                }
            });
        }

        function loadDefaultAlarmRule() {
            var url = "${_base}/usr/applications/alarm-rule/global";
            $.ajax({
                type: 'POST',
                url: url,
                dataType: 'json',
                success: function (data) {
                    if (data.code == "200") {
                        if (data.result == "") {
                            $("#period").val("");
                            $("#mailTo").val("");
                            $("#mailCc").val("");
                        } else {
                            var globalConfig = jQuery.parseJSON(data.result);
                            $("#globalAlarmRuleId").val(globalConfig.ruleId)
                            $("#period").val(globalConfig.configArgs.period);
                            $("#mailTo").val(globalConfig.configArgs.mailInfo.mailTo);
                            $("#mailCc").val(globalConfig.configArgs.mailInfo.mailCc);
                        }
                    } else {
                        $("#errormessage").text(data.message);
                        $("#errorMessageAlter").show();
                    }
                },
                error: function () {
                    $("#errormessage").text("Fatal error");
                    $("#errorMessageAlter").show();
                }
            });
        }
    </script>
</body>
</html>
