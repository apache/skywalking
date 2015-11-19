function changeFrameUrl(url) {
	console.info('showTraceLog iframe url change: ' + url);
	$("#showTraceLog").attr("src", url);
}

$().ready(function() {

	var baseUrl = $("#baseUrl").val();
	var traceId = $("#traceId").val();
	
	/** 搞个默认值测试*/
	traceId = "933b360f94294833b6a82351d4ded676123";

	changeFrameUrl("");

	/** init page */
	if (traceId != '' && traceId.length > 0) {
		$("#srchKey").val(traceId);
		var srchKey = $("#srchKey").val();
		if (srchKey != "") {
			changeFrameUrl(baseUrl + "showTraceLog/" + srchKey);
		}
	} else {
		$("#srchKey").val("");
	}

	/** bind srchBtn */
	$("#srchBtn").bind("click", function() {
		var srchKey = $("#srchKey").val();
		if (srchKey != "") {
			changeFrameUrl(baseUrl + "showTraceLog/" + srchKey);
		}
	});

	$("a[name='menuUrl']").each(function() {
		$(this).bind('click', function() {
			changeFrameUrl(baseUrl + $(this).attr("url"));
		});
	});

});
