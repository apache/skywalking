/**
 * @author pengys5
 */

function showSpanModal(spanSegId) {
    $.getJSON("/spanDataLoad?spanSegId=" + spanSegId, function (data) {
        var spanModalTemplate = $.templates("#spanModalTemplate");

        data.startTime = moment(data.startTime).format("YYYY/MM/DD HH:mm:ss SSS");
        data.endTime = moment(data.endTime).format("YYYY/MM/DD HH:mm:ss SSS");

        spanModalTemplate.link("#spanModalDiv", data);
        $("#spanModalDiv").modal("toggle");
    });
}