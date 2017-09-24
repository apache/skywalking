define(["vue", "text!./head.html", "bootstrap"], function (Vue, head) {
    var header = Vue.extend({
        template: head
    });

    Vue.component("sw-header", header);

    new Vue({
        el: "#header"
    });
});