$().ready(function() {

	var baseUrl = $("#baseUrl").val();
	console.info(baseUrl);

	/** init page */
	$("#srchKey").val("");
	$("#showTraceLog").attr("src", "");

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

function changeFrameUrl(url){
	console.info('iframe url change1: ' + url);
	$("#showTraceLog").attr("src", url);
}