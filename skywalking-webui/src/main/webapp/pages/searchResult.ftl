<#import "./commons/commons.ftl" as common>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta http-equiv="content-type" content="text/html; charset=UTF-8">
    <title>Skywalking</title>
    <link href="${_base}/node_modules/bootstrap/dist/css/bootstrap.min.css" rel="stylesheet"/>
    <script src="${_base}/node_modules/jquery/dist/jquery.min.js"></script>
    <script src="${_base}/node_modules/bootstrap/dist/js/bootstrap.min.js"></script>
</head>
<body>
<div class="container">
<@common.navbar/>
    <div class="row">
        <div class="col-lg-3">
            <img src="http://www.baidu.com/img/bd_logo1.png"/>
        </div>
        <div class="col-lg-6">
            <div class="input-group">
                <input type="text" id="searchKey" name="searchKey" class="form-control" placeholder="Search for...">
                <span class="input-group-btn">
                    <button id="searchBtn" name="searchBtn" class="btn btn-success" type="button">Search</button>
                </span>
            </div>
        </div>
    </div>
</div>
</body>