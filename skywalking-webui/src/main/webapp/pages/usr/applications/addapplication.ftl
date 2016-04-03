<#import "../../common/commons.ftl" as common>
<!DOCTYPE html>
<html lang="zh-CN">

<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"/>
<@common.importResources />
    <link href="${_base}/node_modules/bootstrap-toggle/css/bootstrap-toggle.min.css" rel="stylesheet">
    <script src="${_base}/node_modules/bootstrap-toggle/js/bootstrap-toggle.min.js"></script>
</head>

<body style="padding-top:80px">
<@common.navbar/>
<div class="container">
    <div class="row" style="display: none;" id="defautConfigAlter">
        <div class="col-md-6 col-md-offset-2">
            <div class="alert alert-warning alert-dismissible" role="alert">
                <button type="button" class="close" data-dismiss="alert" aria-label="Close"><span
                        aria-hidden="true">&times;</span></button>
                <strong>Warning!</strong>&nbsp;You don't have a global config,
                you may <a href="${_base}/usr/applications/alarm-rule/global/create">
                <ins>create global config</ins>
            </a>.
            </div>
        </div>
    </div>
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
                        <label for="appCode" class="col-sm-3 control-label">应用编码:</label>
                        <div class="col-sm-9">
                            <input type="text" class="form-control" id="appCode" placeholder="Application code">
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
                            <button type="button" id="saveBtn" data-loading-text="Loading..." class="btn btn-primary"
                                    autocomplete="off">
                                <strong><ins>Save</ins></strong>
                            </button>
                        </div>
                        <div class="col-sm-offset-1 col-sm-1">
                            <button type="button" class="btn btn-default"><strong><ins>Cancle</ins></strong></button>
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
                    $("#sysConfigParam input").each(function () {
                        $(this).prop("disabled", true);
                    });
                    $("#isModifyGlobalConfig").show();
                } else {
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

            $("#saveBtn").click(function () {
                var url = "${_base}/usr/applications/dosave";
                $.ajax({
                    type: 'POST',
                    url: url,
                    data: {
                        "appInfo": function () {
                            var application = {};
                            application.appCode = $("#appCode").val();
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
            loadDefaultAlarmRule();
        });

        function loadDefaultAlarmRule() {
            var url = "${_base}/usr/applications/alarm-rule/global";
            $.ajax({
                type: 'POST',
                url: url,
                dataType: 'json',
                success: function (data) {
                    if (data.code == "200") {
                        if (data.result == "") {
                            $("#defautConfigAlter").show();
                            $("#isGlobalConfig").prop("checked", false).change();
                            $("#isGlobalConfig").prop("disabled", "disabled");
                            $("#isModifyGlobalConfig").hide();
                        } else {
                            $("#isGlobalConfig").prop("checked", true).change();
                            $("#isModifyGlobalConfig").show();
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
                    $("#errormessage").text("Fatal Message");
                    $("#errorMessageAlter").show();
                }
            });
        }
    </script>
</body>
</html>
