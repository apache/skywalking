<#import "./lib/ai.cloud/common.ftl" as common>
<!DOCTYPE html>
<html lang="zh-CN">
<html>
<head>
    <meta charset="utf-8">
    <title>traceLog</title>
    <link rel="stylesheet" href="${base}/css/jquery.treetable.css"/>
    <link rel="stylesheet" href="${base}/css/jquery.treetable.theme.default.css"/>
    <link rel="stylesheet" href="${base}/css/bootstrap.css"/>
    <link rel="stylesheet" href="${base}/css/traceLog.css?1=1"/>
    <!-- script references -->
<@common.importJavaScript />
</head>
<body>

<!-- show traceLogInfo -->
<@common.dealTraceLog />
<!-- show originLog -->
<@common.importOriginLog />
<script>
    $().ready(function () {
        var table = $('#example-advanced').children();
        $("#example-advanced").treetable({expandable: true, indent: 10, clickableNodeNames: true});

        $("#example-advanced tr").click(function () {
            var selected = $(this).hasClass("highlight");
            $("#example-advanced tr").removeClass("highlight");
            if (!selected)
                $(this).addClass("highlight");
        });

        $("#originLog").bind("click", function () {
            if ($(this).html() == "显示原文") {
                $("#originRow").slideDown("slow", function () {
                    $("#tableDiv").slideUp("slow");
                });
                $(this).html("显示调用链");
            } else {
                $("#tableDiv").slideDown("slow", function () {
                    $("#originRow").slideUp("slow");
                });
                $(this).html("显示原文");
            }
        });

        $("tr[name='log']").each(function () {
            var code = $(this).attr("statusCodeStr");
            if (code != 0 || code == '') {
                var node = $(this).attr("data-tt-id");
                $(this).css("color", "red");
            }
        });

        $("a[name='detailInfo']").each(function (index, ele) {
            $(this).bind("click", function () {
                $("#detailContent").html($("#collapse" + index).html());
                $("#detailLog").modal('show');
            });
        });

        $('#example-advanced').treetable('expandAll');
    });
</script>
</body>
</html>
