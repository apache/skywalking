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
<link href="${base}/css/index.css" rel="stylesheet">
</head>
<body>
	<input type="hidden" id="baseUrl" value="${base}">
	<input type="hidden" id="traceId" value="${traceId!''}">
	<div class="navbar navbar-inverse navbar-fixed-top">
		<div class="container">
			<div class="navbar-header">
				<a class="navbar-brand" href="./">Sky Walking</a>
			</div>
			<div class="collapse navbar-collapse">
				<!-- 菜单（不要太多） -->
				<@common.importMenuInfo menuInfo="${userInfo}" />
				
				<!-- 搜索栏 -->
				<@common.importSearchInfo isLogin="${userInfo}" />
				
				<!-- 登录/用户信息块 -->
				<@common.importUserInfo userInfo="${userInfo}" />
				
			</div>
			<!--/.nav-collapse -->

		</div>
	</div>

	<div>
		<div class="text-center" style="height: 700px">
			<iframe id="showTraceLog" border=2 frameborder=0 width=100%
				height=100% marginheight=0 marginwidth=0 scrolling=yes src=""></iframe>
		</div>
	</div>
	<!-- script references -->
	<@common.importJavaScript />
	<script src="${base}/js/webui-0.1.js"></script>
</body>
</html>