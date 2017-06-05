/**
 * @author pengys5
 */

function showSpanModal(spanSegId) {
    $.getJSON("/spanDataLoad?spanSegId=" + spanSegId, function (data) {
        var spanModalTemplate = $.templates("#spanModalTemplate");
        spanModalTemplate.link("#spanModalDiv", data);
        $("#spanModalDiv").modal("toggle");
    });
}

$.views.converters({
    dateFormat: function (val) {
        return moment(val).format("YYYY/MM/DD HH:mm:ss SSS");
    },

    tabCharacter: function (val) {
        console.log(val);
        return replaceAll(val);
    }
});

function replaceAll(str) {
    if (str != null){
        str = str.replace(/\\n\\t/ig, "<br />");
        str = str.replace(/\\n/ig, "<br />");
    }
    return str;
}