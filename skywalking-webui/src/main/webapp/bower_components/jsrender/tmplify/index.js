/*! JsRender tmplify submodule v0.9.74 (Beta): http://jsviews.com/#jsrender */
/*! Browserify transform for JsRender templates */
/*
 * Copyright 2016, Boris Moore
 * Released under the MIT License.
*/

(function() {
"use strict";
var jsrender = require('./../jsrender-node.js'),
	path = require('path'),
	pathSep = path.sep,
	through = require('through2'),
	rootDirNameLen = path.resolve("./").length + 1;

function isTemplate(fileExt, extensions) {
	extensions = typeof extensions === "string"
		? extensions
		: "html jsrender jsr"; // Default extensions
	return new RegExp("\\s" + fileExt + "\\s").test(" " + extensions + " ");
}

module.exports = function(file, options) {
	var nodeFileDirName = path.dirname(file);

	if (!isTemplate(path.extname(file).slice(1), options && (options.extensions || options.e))) {
		return through();
	}

	return through(function(buf, enc, next) {
		var createTmplCode, ref, pathFromFileDir,
			markup = buf.toString().replace(/^\uFEFF/, ''), // Remove BOM if necessary
			tmpl = jsrender.templates(markup),
			bundledFile = 'var tmplRefs = [],\n'
			+ "  mkup = '" + markup.replace(/['"\\]/g, "\\$&").replace(/[ \t]*(\r\n|\n|\r)/g, '\\n') + "',\n" // Normalize newlines, and escape quotes and \ character
			+ '  $ = global.jsrender || global.jQuery;\n\n',
			templateName = './' + file.slice(rootDirNameLen).split(pathSep).join('/');

		for (ref in tmpl.refs) {
			// Recursively bundle any nested template references, e.g. {{include tmpl="./some/template.html/}}"
			pathFromFileDir = './' + path.relative(nodeFileDirName, ref).split(pathSep).join('/');
			bundledFile += 'tmplRefs.push(require("' + pathFromFileDir + '"));\n';
		}

		createTmplCode = '$.templates("' + templateName + '", mkup)';
		bundledFile +=
			'module.exports = $ ? ' + createTmplCode
			+ ' :\n  function($) {\n'
			+ '    if (!$ || !$.views) {throw "Requires jsrender/jQuery";}\n'
			+ '    while (tmplRefs.length) {\n      tmplRefs.pop()($); // compile nested template\n    }\n\n'
			+ '    return ' + createTmplCode
			+ '\n  };';
		this.push(bundledFile);
		next();
	});
};
}());
