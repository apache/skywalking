<#import "../common/commons.ftl" as common>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"/>
<@common.importResources />
    <script src="${_base}/bower_components/skywalking/js/jquery-md5.js"></script>
</head>
<body>
<div class="container-fluid">
    <div clas="row">
        <div class="col-md-4 col-md-offset-4">
            <img src="${_base}/bower_components/skywalking/img/logo.png" class="img-responsive center-block"/>
        </div>
    </div>
    <div class="row">
        <div class="col-md-4 col-md-offset-4">
            <div class="panel panel-default">
                <div class="panel-heading">
                    <h1  class="panel-title">Welcome to join skywalking</h1>
                </div>
                <div class="panel-body">
                    <div class="alert alert-warning alert-dismissible" role="alert" id="alertWarnMessageBox"
                         style="display:none">
                        <button type="button" class="close" data-dismiss="alert" aria-label="Close"><span
                                aria-hidden="true">&times;</span></button>
                        <strong>Warning!</strong>
                        <p id="errorMessage"></p>
                    </div>
                    <div class="alert alert-success alert-dismissible" role="alert" id="alertSuccessMessageBox"
                         style="display:none">
                        <button type="button" class="close" data-dismiss="alert" aria-label="Close"><span
                                aria-hidden="true">&times;</span></button>
                        <strong>Congratulate!</strong>
                        <p>You have successfully registered!<a href="${_base}/usr/login"><ins>Go to sign in</ins></a></p>
                    </div>
                    <form role="form">
                        <fieldset>
                            <div class="form-group">
                                <input type="text" autofocus="" name="userName" id="userName"
                                       placeholder="User name"
                                       class="form-control">
                            </div>
                            <div class="form-group">
                                <input type="password" value="" name="password" id="password"
                                       placeholder="Password"
                                       class="form-control">
                            </div>
                            <a id="loginBtn" class="btn btn-lg btn-success btn-block"
                               href="javascript:void(0);">sign up</a>
                        </fieldset>
                    </form>
                </div>
                <div class="panel-footer text-center">
                    <p>Have an account? <a href="${_base}/usr/login"> To sign in</a></p>
                </div>
            </div>
        </div>
    </div>
</div>
<script>
    $(document).ready(function () {
        $("#loginBtn").click(function () {
            $("#alertMessageBox").hide();
            var url = "${_base}/usr/doRegister"
            $.ajax({
                type: 'POST',
                url: url,
                data: {
                    userName: function () {
                        return $("#userName").val();
                    },
                    password: function () {
                        return $.md5($("#password").val());
                    }
                },
                dataType: 'json',
                success: function (data) {
                    if (data.code != '200') {
                        $("#errorMessage").text(data.message);
                        $("#alertWarnMessageBox").show();
                        return;
                    }else{
                        $("#alertSuccessMessageBox").show();
                    }
                },
                error: function () {
                    $("#errorMessage").text("Fatal Error, please try it again.");
                    $("#alertMessageBox").show();
                }
            });
        });
    });
</script>
</body>
</html>
