<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta http-equiv="content-type" content="text/html; charset=UTF-8">
    <title>Skywalking</title>
    <link href="${_base}/node_modules/bootstrap/dist/css/bootstrap.min.css" rel="stylesheet"/>
    <script src="${_base}/node_modules/jquery/dist/jquery.min.js"></script>
    <script src="${_base}/node_modules/bootstrap/dist/js/bootstrap.min.js"></script>
    <style>
        .login-panel {
            margin-top: 25%;
        }
    </style>
</head>
<body>
<div class="container ">
    <div class="container">
        <div class="row">
            <div class="col-md-4 col-md-offset-4">
                <div class="login-panel panel panel-default">
                    <div class="panel-heading">
                        <h3 class="panel-title">Please Sign In</h3>
                    </div>
                    <div class="panel-body">
                        <form role="form">
                            <fieldset>
                                <div class="form-group">
                                    <input type="email" autofocus="" name="email" placeholder="E-mail"
                                           class="form-control">
                                </div>
                                <div class="form-group">
                                    <input type="password" value="" name="password" placeholder="Password"
                                           class="form-control">
                                </div>
                                <div class="checkbox">
                                    <label>
                                        <input type="checkbox" value="Remember Me" name="remember">Remember Me
                                    </label>
                                </div>
                                <!-- Change this to a button or input when using this as a form -->
                                <a class="btn btn-lg btn-success btn-block" href="index.html">Login</a>
                            </fieldset>
                        </form>
                    </div>
                </div>
            </div>
        </div>
    </div>
</body>