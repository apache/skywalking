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
    <link href="${base}/css/login.css" rel="stylesheet">
<@common.importJavaScript />
</head>
<body>
<div class="top-context">
<@common.importHeaderInfo userInfo="${userInfo}"/>
    <div class="row">
        <div class="col-md-4 col-sm-offset-4" style="margin-top: 100px">
            <div class="form-top">
                <div class="form-top-left" style="color:white">
                    <h3>Welcome to register!</h3>
                    <p>Enter your username and password to register our site:</p>
                </div>
                <div class="form-top-right">
                    <i class="fa fa-key"></i>
                </div>
            </div>
            <div class="form-bottom">
                <div class="form-group">
                    <input id="loginName" type="text" class="form-control input-lg" placeholder="用户名/邮箱"
                           autofocus>
                </div>
                <div class="form-group">
                    <input id="loginPassword" type="password"
                           class="form-control input-lg" placeholder="密码">
                </div>
                <button id="registBtn" class="btn btn-lg btn-primary btn-block">Register</button>
            </div>
        </div>
    </div>
</div>
<!-- script references -->
<script type="text/javascript">
    $().ready(function () {
        $("#registBtn").bind("click", function () {
            if ($("#loginName").val() == '') {
                alert("请输入用户名");
                return false;
            }
            if ($("#loginPassword").val() == '') {
                alert("请输入密码");
                return false;
            }
            var urlStr = '${base}/regist/' + $("#loginName").val() + '/' + $.md5($("#loginPassword").val());
            var refUrl = '${base}/login';
            $.ajax({
                type: 'POST',
                url: urlStr,
                data: {},
                dataType: 'json',
                async: false,
                success: function (data) {
                    console.log(data);
                    var result = data.result;
                    if (result == 'OK') {
                        alert(data.msg);
                        window.location.href = refUrl;
                        //window.parent.changeFrameUrl(refUrl);
                    } else {
                        alert(data.msg);
                    }
                },
                error: function (xhr, type) {
                    alert("注册失败");
                }
            });
        })
    });
</script>
</body>
</html>