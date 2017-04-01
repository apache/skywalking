/**
 * @author pengys5
 */

function showSpanModal(spanSegId) {
    $.getJSON("/spanDataLoad?spanSegId=" + spanSegId, function (data) {
        var spanModalTemplate = $.templates("#spanModalTemplate");

        data.st = moment(data.st).format("YYYY/MM/DD HH:mm:ss SSS");
        data.et = moment(data.et).format("YYYY/MM/DD HH:mm:ss SSS");

        spanModalTemplate.link("#spanModalDiv", data);
        $("#spanModalDiv").modal("toggle");
    });
}