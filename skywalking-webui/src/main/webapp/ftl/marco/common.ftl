
<#-- script -->
<#macro importJavaScript>
<script src="${base}/js/jquery/jquery-2.1.4.js"></script>
<script src="${base}/js/jquery/jquery-ui-1.11.4.js"></script>
<script src="${base}/js/jquery/jquery.treetable-3.2.0.js"></script>
<script src="${base}/js/bootstrap.min-3.3.5.js"></script>

</#macro>

<#-- menuInfo -->
<#macro importMenuInfo menuInfo>
<ul class="nav navbar-nav">
<#assign text>
${menuInfo}
</#assign>
<#assign json=text?eval />
<#if json.isLogin == '1'>
<li><a name="menuUrl" href="#" url="user/useDoc/${json.uid}">使用说明</a></li>
<li><a name="menuUrl" href="#" url="user/">告警配置</a></li>
<#else>

</#if>
</ul>
</#macro>

<#-- SearchInfo -->
<#macro importSearchInfo isLogin>
<form class="navbar-form navbar-left" role="search">
	<div class="form-group">
<#if isLogin == '1'>
		<input id="srchKey" type="text" class="form-control" style="width: 450px" placeholder="TraceId">
<#else>
		<input id="srchKey" type="text" class="form-control" style="width: 750px" placeholder="TraceId">
</#if>
</div>
	<button id="srchBtn" type="button" class="btn btn-default">Search</button>
</form>
</#macro>

<#-- userInfo -->
<#macro importUserInfo userInfo>
<ul class="nav navbar-nav navbar-right">
<#assign text>
${userInfo}
</#assign>
<#assign json=text?eval />
<#if json.isLogin == '1'>
	<li class="dropdown"><a href="#" class="dropdown-toggle"
		data-toggle="dropdown" role="button" aria-haspopup="true"
		aria-expanded="false"> ${json.userName} <span class="caret"></span></a>
		<ul class="dropdown-menu">
			<li><a name="menuUrl" href="#" url="user/setting/${json.uid}">设置</a></li>
			<li role="separator" class="divider"></li>
			<li><a name="menuUrl" href="#" url="logout">退出</a></li>
		</ul>
	</li>
<#else>
	<li><a name="menuUrl" href="#" url="login">登录</a></li>
</#if>
</ul>
</#macro>