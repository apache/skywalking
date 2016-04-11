function del(applicationId) {
    var baseUrl = $("#baseUrl").text();
    var url = baseUrl + "/usr/applications/del/" + applicationId;
    $.ajax({
        type: 'POST',
        url: url,
        dataType: 'json',
        success: function (data) {
            if (data.code == 200) {
                $("#allApplications").empty();
                loadAllApplications();
            } else {
                $("#errormessage").text(data.message);
                $("#errorMessageAlert").show();
            }
        },
        error: function () {
            $("#errormessage").text("Fatal error");
            $("#errorMessageAlert").show();
        }
    });
}

function loadAllApplications(){
    var baseUrl = $("#baseUrl").text();
    var url = baseUrl + "/usr/applications/all";
    $.ajax({
        type: 'POST',
        url: url,
        dataType: 'json',
        success: function (data) {
            if (data.code == 200) {
                var template = $.templates("#applicationsAllTmpl");
                var htmlOutput = template.render({applications:jQuery.parseJSON(data.result)});
                $("#mainPanel").empty();
                $("#mainPanel").html(htmlOutput);
            } else {
                $("#errormessage").text(data.message);
                $("#errorMessageAlert").show();
            }
        },
        error: function () {
            $("#errormessage").text("Fatal error");
            $("#errorMessageAlert").show();
        }
    });
}

function addApplication(){
    $('#isGlobalConfig').bootstrapToggle();
    var baseUrl = $("#baseUrl").text();
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

    $("#saveApplicationBtn").click(function () {
        var url = baseUrl + "/usr/applications/dosave";
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
                    $("#successCreatedMessageAlter").show();
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
}

function loadDefaultAlarmRule() {
    var baseUrl = $("#baseUrl").text();
    var url = baseUrl + "/usr/applications/alarm-rule/global";
    $.ajax({
        type: 'POST',
        url: url,
        dataType: 'json',
        success: function (data) {
            if (data.code == "200") {
                if (data.result == "") {
                    $("#globalConfigAlter").show();
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

function createGlobalConfig(){
    $('button[data-loading-text]').click(function () {
        //var btn = $(this).button('loading');
        var baseUrl = $("#baseUrl").text();
        var url = baseUrl +"/usr/applications/alarm-rule/global/save"
        $.ajax({
            type: 'POST',
            url: url,
            data: {
                "configArgs": function () {
                    var configArg = {};
                    var mailInfo = {};
                    configArg.period = parseInt($("#period").val());
                    mailInfo.mailTo = $("#mailTo").val().split(",");
                    mailInfo.mailCc = $("#mailCc").val().split(",");
                    configArg.mailInfo = mailInfo;
                    return JSON.stringify(configArg);
                }
            },
            dataType: 'json',
            success: function (data) {
                if (data.code == "200") {
                    $("#saveGlobalSuccessAlter").show();
                } else {
                    $("#saveGlobalFailedConfigAlter").show();
                    $("#errormessage").text(data.message);
                }
                //btn.button('reset');
            },
            error: function () {
                $("#saveGlobalFailedConfigAlter").show();
                $("#errormessage").text("Fatal error!");
            }
        });

    });
}

function loadAlarmRule(applicationId) {
    var baseUrl = $("#baseUrl").text();
    var url = baseUrl +"/usr/applications/alarm-rule/load/" + applicationId;
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
    var baseUrl = $("#baseUrl").text();
    var url = baseUrl +"/usr/applications/load/" + applicationId;
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
            $("#errorModifiedMessageAlter").show();
        }
    });
}

function loadDefaultAlarmRuleData() {
    var baseUrl = $("#baseUrl").text();
    var url = baseUrl +"/usr/applications/alarm-rule/global";
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
                $("#errorModifiedMessageAlter").show();
            }
        },
        error: function () {
            $("#errormessage").text("Fatal error");
            $("#errorModifiedMessageAlter").show();
        }
    });
}

function modifyApplication(){
    $('#isGlobalConfig').bootstrapToggle();
    $("#isGlobalConfig").change(function () {
        if ($(this).prop("checked")) {
            //替换成
            loadDefaultAlarmRuleData($("#applicationId").val());
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
        var baseUrl = $("#baseUrl").text();
        var url = baseUrl +"/usr/applications/update/" + $("#applicationId").val();
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
                    $("#successModifiedMessageAlter").show();
                } else {
                    $("#errormessage").text(data.message);
                    $("#errorModifiedMessageAlter").show();
                }
            },
            error: function () {
                $("#errormessage").text("Fatal error");
                $("#errorModifiedMessageAlter").show();
            }
        });
    });
    loadApplicationInfo($("#applicationId").val());
}

function toDownloadAuthFile(){
    $("#downloadBtn").click(function () {
        downloadAuthFile();
    })
}

function downloadAuthFile() {
    var baseUrl = $("#baseUrl").text();
    var applicationId = $("#applicationId").val();
    if (applicationId == "") {
        $("#message").text("Application Id cannot be null");
        $("#warningAlter").show();
        return;
    }
    var form = $("<form>");
    form.attr("style", "display:none");
    form.attr("method", "post");
    form.attr("action", baseUrl + "/usr/applications/authfile/download/" + applicationId);
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
    $("#authFiledownLoad").append(form);
    form.append(exportData);
    form.append(exclusiveException);
    form.append(authType);
    form.submit();
}