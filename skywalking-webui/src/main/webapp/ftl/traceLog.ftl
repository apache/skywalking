<#import "./lib/ai.cloud/common.ftl" as common>
<!DOCTYPE html>
<html lang="zh-CN">
<html>
  <head>
    <meta charset="utf-8">
    <title>traceLog</title>
    <link rel="stylesheet" href="${base}/css/jquery.treetable.css" />
    <link rel="stylesheet" href="${base}/css/jquery.treetable.theme.default.css" />
	<link rel="stylesheet" href="${base}/css/bootstrap.css" />
	<link rel="stylesheet" href="${base}/css/traceLog.css" />
  </head>
  <body>
  	
  	<!-- show traceLogInfo -->
    <@common.dealTraceLog />

    <!-- script references -->
	<@common.importJavaScript />
    <script>
		var table = $('#example-advanced').children();  
		$("#example-advanced").treetable({ expandable: true , indent : 10, clickableNodeNames : true});
		
		$("#example-advanced tr").click(function() {
			var selected = $(this).hasClass("highlight");
			$("#example-advanced tr").removeClass("highlight");
			if(!selected)
            $(this).addClass("highlight");
		});
		
    </script>
  </body>
</html>
