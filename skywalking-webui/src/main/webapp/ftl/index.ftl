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
    <link href="${base}/css/index.css" rel="stylesheet">
    <style>
        .leaf {
            background-color: white;
        }
    </style>
<@common.importJavaScript />
</head>
<body>
<div class="container">
<@common.importHeaderInfo userInfo="${userInfo}"/>
    <div>
        <div class="col-md-12" style="position:absolute;left:0px;right:0px;margin-top:20px;margin-left: 6px;"
             id="showTraceLog">
        </div>
    </div>
    <input type="hidden" id="uid" value="${(userInfo?eval).uid!''}">
    <script src="${base}/js/webui-0.1.js?1=1"></script>
</div>
</body>
</html>