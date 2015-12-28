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
<div class="form-horizontal">
    <div class="form-group">
        <label for="period" class="col-sm-4 control-label">需要排除的异常</label>
        <div class="col-sm-4">
            <input type="text" class="form-control" id="exclusiveException"
                   placeholder="java.lang.Exception,java.io.IOException">
        </div>
    </div>
    <div class="form-group">
        <label for="period" class="col-sm-4 control-label">授权文件类型:</label>
        <div class="col-sm-4">
            <select class="form-control" id="authType">
                <option value="1">外网</option>
                <option value="0">内网</option>
            </select>
        </div>
    </div>
    <input type='hidden' id="authAppCode" value="${appCode}">
</div>
</body>
</html>