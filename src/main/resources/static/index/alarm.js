/**
 * @author pengys5
 */
window.onresize = function () {
    resizeAlarmListDivSize();
}

$(document).ready(function () {
    resizeAlarmListDivSize();
});

function resizeAlarmListDivSize() {
    var height = $(window).height();
    $("#alarmListDiv").height(height - 130);
}