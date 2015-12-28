<#import "../lib/ai.cloud/common.ftl" as common>
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
</head>
<body>
<div class="form-horizontal" id="crtAlarmDiv">
    <div class="form-group">
        <label class="col-sm-4 control-label">应用名称：</label>
        <div class="col-sm-4">
            <p class="form-control-static" id="appName">${ruleMVO.appCode}</p>
        </div>
    </div>
    <div class="form-group">
        <label for="period" class="col-sm-4 control-label">告警频率：</label>
        <div class="col-sm-4">
            <input type="text" class="form-control" id="period" placeholder="告警发送时间间隔(秒)"
                   value="${(ruleMVO.configArgs.period)!''}">
        </div>
    </div>
    <div class="form-group">
        <label for="todoType" class="col-sm-4 control-label">告警操作：</label>
        <div class="col-sm-4">
            <p class="form-control-static">发送邮件</p>
            <input id="todoType" type="hidden" value="0"/>
        </div>
    </div>
    <div id="mailTempDiv" style="display:block">
        <div class="form-group">
            <label for="mailTemp" class="col-sm-4 control-label">收件人地址：</label>
            <div class="col-sm-4">
<textarea class="form-control" id="mailTo" rows="3" placeholder="收件人地址，请英文逗号,分割"><#if (ruleMVO.configArgs.mailInfo.mailTo)??><#list ruleMVO.configArgs.mailInfo.mailTo!'' as node>${node},</#list></#if></textarea>
            </div>
        </div>
        <div class="form-group">
            <label for="mailCc" class="col-sm-4 control-label">抄送人地址：</label>
            <div class="col-sm-4">
                <textarea class="form-control" id="mailCc" rows="3" placeholder="抄送人地址，请英文逗号,分割"><#if (ruleMVO.configArgs.mailInfo.mailCc)??><#list ruleMVO.configArgs.mailInfo.mailCc as node>${node},</#list></#if></textarea>
            </div>
        </div>
        <input type='hidden' id="ruleId" value="${(ruleMVO.ruleId)!}">
        <input type='hidden' id="appId" value="${(ruleMVO.appId)!}">
        <input type='hidden' id="isGlobal" value="${(ruleMVO.isGlobal)!}">
    </div>
</div>
</body>
</html>