<#import "./lib/ai.cloud/common.ftl" as common>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta http-equiv="content-type" content="text/html; charset=UTF-8">
<meta charset="utf-8">
<title>Sky Walking</title>
<meta name="generator" content="Bootply" />
<meta name="viewport"
	content="width=device-width, initial-scale=1, maximum-scale=1">
<link href="${base}/css/bootstrap.min.css" rel="stylesheet">
<!--[if lt IE 9]>
			<script src="//html5shim.googlecode.com/svn/trunk/html5.js"></script>
		<![endif]-->
<link href="${base}/css/login.css" rel="stylesheet">
</head>
<body>
	<div class="container">
		<form class="form-signin">
			<h2 class="form-signin-heading">登录</h2>
			<label for="inputEmail" class="sr-only">用户名/邮箱</label> 
				<input id="a" type="text" class="form-control" placeholder="用户名"
				autofocus> 
			<label for="inputPassword" class="sr-only">密码</label> 
				<input id="b" type="password" id="inputPassword"
				class="form-control" placeholder="密码">
			<button id="login" class="btn btn-lg btn-primary btn-block" type="submit">登录</button>
		</form>
	</div>
	<!-- script references -->
	<@common.importJavaScript />
	<script type="text/javascript">
		$().ready(function(){
			$("#login").bind("click",function(){
				if($("#a").val() == ''){
					alert("请输入用户名");
					return false;
				}
				if($("#b").val() == ''){
					alert("请输入密码");
					return false;
				}
				var urlStr = '${base}/login/'+$("#a").val()+'/'+$.md5($("#b").val());
				$.ajax({
					type: 'POST',
					url: urlStr,
					data:{},
					dataType: 'json',
					async : false,
					success: function(data){
						console.log(data);
						var result = data.result;
						if(result == 'OK'){
							top.location.href='${base}';
						}else{
							alert(data.msg);
						}
					},
					error: function(xhr, type){
						alert("验证失败");
					}
				});
			})
		});
	</script>
</body>
</html>