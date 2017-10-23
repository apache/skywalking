/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking-ui
 */

/**
 * @author peng-yongsheng
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