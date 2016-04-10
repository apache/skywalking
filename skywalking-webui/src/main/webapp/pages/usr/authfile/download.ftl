<#import "../../common/commons.ftl" as common>
<!DOCTYPE html>
<html lang="zh-CN">

<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"/>
<@common.importResources />
    <title>Download auth file</title>
</head>

<body style="padding-top:80px">
<@common.navbar/>
<div class="container">
    <div class="row" style="display: none;" id="warningAlter">
        <div class="col-md-6 col-md-offset-2">
            <div class="alert alert-warning alert-dismissible" role="alert">
                <button type="button" class="close" data-dismiss="alert" aria-label="Close"><span
                        aria-hidden="true">&times;</span></button>
                <strong>Warning!</strong>&nbsp;<p id="message"></p>
            </div>
        </div>
    </div>
    <div class="row">
        <div class="col-md-6 col-md-offset-2">
            <div class="row">
                <form class="form-horizontal" role="form">
                    <input type='hidden' id="applicationId" value="${applicationId}">
                    <div class="form-group">
                        <label for="exclusiveException" class="col-sm-3 control-label">需要排除的异常:</label>
                        <div class="col-sm-9">
                            <input type="text" class="form-control" id="exclusiveException"
                                   placeholder="java.lang.Exception,java.io.IOException">
                        </div>
                    </div>
                    <div class="form-group">
                        <label for="authType" class="col-sm-3 control-label">授权文件类型:</label>
                        <div class="col-sm-9">
                            <select class="form-control" id="authType" name="authType">
                                <option value="1">外网</option>
                                <option value="0">内网</option>
                            </select>
                        </div>
                    </div>
                    <div class="form-group">
                        <div class="col-sm-offset-4 col-sm-1">
                            <button type="button" id="downloadBtn" data-loading-text="Loading..."
                                    class="btn btn-primary"
                                    autocomplete="off">
                                <strong>
                                    <ins>Download</ins>
                                </strong>
                            </button>
                        </div>
                        <div class="col-sm-offset-1 col-sm-1">
                            <button type="button" class="btn btn-default"><strong>
                                <ins>Cancle</ins>
                            </strong></button>
                        </div>
                    </div>
                </form>
            </div>
        </div>
    </div>
</div>
<div id="authFiledownLoad">
</div>
<script>

    $(document).ready(function () {
        $("#downloadBtn").click(function () {
            downloadAuthFile();
        })
    });

    function downloadAuthFile() {
        var applicationId = $("#applicationId").val();
        if (applicationId == "") {
            $("#message").text("Application Id cannot be null");
            $("#warningAlter").show();
            return;
        }
        var form = $("<form>");
        form.attr("style", "display:none");
        form.attr("method", "post");
        form.attr("action", "${_base}/usr/applications/authfile/download/" + applicationId);
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
</script>
</body>
</html>
