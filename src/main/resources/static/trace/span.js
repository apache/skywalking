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
        var aa = replaceAll(val);
        console.log(aa);
        return aa;
    }
});

function replaceAll(str) {
    if (str != null){
        str = str.replace(/\n\t/ig, "<br />");
    }
    return str;
}