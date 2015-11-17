function changeFrameUrl(url){
	console.info('showTraceLog iframe url change: ' + url);
	$("#showTraceLog").attr("src", url);
}

$().ready(function() {

	var baseUrl = $("#baseUrl").val();
	var traceId = $("#traceId").val();
	

	changeFrameUrl("");
	
	/** init page */
	if(traceId != '' && traceId.length > 0){
		$("#srchKey").val(traceId);
		var srchKey = $("#srchKey").val();
		if (srchKey != "") {
			changeFrameUrl(baseUrl + "showTraceLog");
		}
	}else{
		$("#srchKey").val("");
	}
	
	/** bind srchBtn */
	$("#srchBtn").bind("click", function() {
		var srchKey = $("#srchKey").val();
		if (srchKey != "") {
			changeFrameUrl(baseUrl + "showTraceLog");
		}
	});

	$("a[name='menuUrl']").each(function() {
		$(this).bind('click', function() {
			changeFrameUrl(baseUrl + $(this).attr("url"));
		});
	});
	
});
