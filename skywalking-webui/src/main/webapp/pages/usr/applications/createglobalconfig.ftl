<#import "../../common/commons.ftl" as common>
<!DOCTYPE html>
<html lang="zh-CN">

<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"/>
<@common.importResources />
    <title>To create global configuration</title>
    <link href="${_base}/bower_components/bootstrap-toggle/css/bootstrap-toggle.min.css" rel="stylesheet">
    <script src="${_base}/bower_components/bootstrap-toggle/js/bootstrap-toggle.min.js"></script>
</head>

<body style="padding-top:80px">
<@common.navbar/>
<div class="container">
    <div class="row" style="display: none" id="saveSuccessAlter">
        <div class="col-md-6 col-md-offset-2">
            <div class="alert alert-success alert-dismissible" role="alert">
                <button type="button" class="close" data-dismiss="alert" aria-label="Close"><span
                        aria-hidden="true">&times;</span></button>
                <strong>Congratulate!</strong>&nbsp;Create global success,
                you may <a href="${_base}/usr/applications/add">
                <ins>Create an application</ins>
            </a> or <a href="${_base}/usr/applications/list">
                <ins>See all application</ins>
            </a>.
            </div>
        </div>
    </div>
    <div class="row" style="display: none;" id="defautConfigAlter">
        <div class="col-md-6 col-md-offset-2">
            <div class="alert alert-warning alert-dismissible" role="alert">
                <button type="button" class="close" data-dismiss="alert" aria-label="Close"><span
                        aria-hidden="true">&times;</span></button>
                <strong>Warning!</strong>
                <p id="errormessage"></p>.
            </div>
        </div>
    </div>
    <div class="row">
        <div class="col-md-6 col-md-offset-2">
            <div class="row">
                <form class="form-horizontal">
                    <div class="panel panel-default" id="sysConfigParam">
                        <div class="panel-heading">
                            告警配置
                        </div>
                        <div class="panel-body">
                            <div class="form-group">
                                <label for="inputPassword3" class="col-sm-3 control-label">告警周期:</label>
                                <div class="col-sm-9">
                                    <input type="text" class="form-control" id="period" name="period"
                                           placeholder="10(M)">
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
                                Save it!
                            </button>
                        </div>
                        <div class="col-sm-offset-1 col-sm-1">
                            <button type="button" class="btn btn-default">Cancle</button>
                        </div>
                    </div>
                </form>
            </div>
        </div>
    </div>
    <script>
        $(document).ready(function () {
            $('button[data-loading-text]').click(function () {
                var btn = $(this).button('loading');
                var url = "${_base}/usr/applications/alarm-rule/global/save"
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
                            $("#saveSuccessAlter").show();
                        } else {
                            $("#errormessage").show();
                            $("#errormessage").text(data.message);
                        }
                        btn.button('reset');
                    },
                    error: function () {
                        $("#errormessage").show();
                        $("#errormessage").text("Fatal error!");
                    }
                });

            });
        });
    </script>
</body>
</html>
