function changeFrameUrl(url) {
	console.info('showTraceLog iframe url change: ' + url);
	$("#showTraceLog").attr("src", url);
}

$().ready(function() {

	var baseUrl = $("#baseUrl").val();
	var traceId = $("#traceId").val();
	var uid = $("#uid").val();
	
	/** 搞个默认值测试*/
//	traceId = "6fbbe463f5b74873aecaf9eb3511846e123";

	changeFrameUrl("");

	/** init page */
	if (traceId != '' && traceId.length > 0) {
		$("#srchKey").val(traceId);
		var srchKey = $("#srchKey").val();
		if (srchKey != "") {
			changeFrameUrl(baseUrl + "/showTraceLog/" + srchKey);
		}
	} else {
		if(uid>0){
			changeFrameUrl(baseUrl + "/applist");
		}
		$("#srchKey").val("");
	}

	/** bind srchBtn */
	$("#srchBtn").bind("click", function() {
		var srchKey = $("#srchKey").val();
		if (srchKey != "") {
			changeFrameUrl(baseUrl + "/showTraceLog/" + srchKey);
		}
	});

	$("a[name='menuUrl']").each(function() {
		$(this).bind('click', function() {
			changeFrameUrl(baseUrl + "/" + $(this).attr("url"));
		});
	});
	
	$("#regist").bind('click', function() {
		changeFrameUrl(baseUrl + "/" + $(this).attr("url"));
	});
	
	$("#login").bind('click', function() {
		changeFrameUrl(baseUrl + "/" + $(this).attr("url"));
	});
	
	$("#logout").bind('click', function() {
		var urlStr = baseUrl + '/logout';
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
					window.location.reload();
				}else{
					alert(data.msg);
				}
			},
			error: function(xhr, type){
				alert("退出失败");
			}
		});
	});
	
});
