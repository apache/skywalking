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
    <div class="inner-bg">
    <@common.importHeaderInfo userInfo="${userInfo}"/>
        <div class="row">
            <div class="col-md-4 col-sm-offset-4" style="margin-top: 100px">
                <div class="form-top">
                    <div class="form-top-left" style="color:white">
                        <h3>Login to our site</h3>
                        <p>Enter your username and password to log on:</p>
                    </div>
                    <div class="form-top-right">
                        <i class="fa fa-key"></i>
                    </div>
                </div>
                <div class="form-bottom">
                    <div class="form-group">
                        <input id="loginName" name="loginName" type="text" class="form-control input-lg"
                               placeholder="用户名/邮箱"
                               autofocus>
                    </div>
                    <div class="form-group">
                        <input id="password" name="password" type="password" id="inputPassword"
                               class="form-control input-lg" placeholder="密码">
                    </div>
                    <button class="btn btn-primary btn-lg btn-block" id="loginBtn">Login</button>
                </div>
            </div>
        </div>
    </div>
</div>
<!-- script references -->

<script type="text/javascript">
    $(document).ready(function () {
        $("#loginBtn").bind("click", function () {
            if ($("#loginName").val() == '') {
                alert("请输入用户名");
                return false;
            }
            if ($("#password").val() == '') {
                alert("请输入密码");
                return false;
            }
            var urlStr = '${base}/login/' + $("#loginName").val() + '/' + $.md5($("#password").val());
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
                        top.location.href = '${base}';
                    } else {
                        alert(data.msg);
                    }
                },
                error: function (xhr, type) {
                    alert("验证失败");
                }
            });
        });
    });
</script>
</body>
</html>