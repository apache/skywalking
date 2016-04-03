/**
 * Copyright 2014 Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
/*global exports:true*/

var Syntax = require('esprima-fb').Syntax;
var utils = require('../src/utils');
var reserverdWordsHelper = require('./reserved-words-helper');

/**
 * Code adapted from https://github.com/spicyj/es3ify
 * The MIT License (MIT)
 * Copyright (c) 2014 Ben Alpert
 */

function visitProperty(traverse, node, path, state) {
  utils.catchup(node.key.range[0], state);
  utils.append('"', state);
  utils.catchup(node.key.range[1], state);
  utils.append('"', state);
  utils.catchup(node.value.range[0], state);
  traverse(node.value, path, state);
  return false;
}

visitProperty.test = function(node) {
  return node.type === Syntax.Property &&
    node.key.type === Syntax.Identifier &&
    !node.method &&
    !node.shorthand &&
    !node.computed &&
    reserverdWordsHelper.isES3ReservedWord(node.key.name);
};

function visitMemberExpression(traverse, node, path, state) {
  traverse(node.object, path, state);
  utils.catchup(node.property.range[0] - 1, state);
  utils.append('[', state);
  utils.catchupWhiteSpace(node.property.range[0], state);
  utils.append('"', state);
  utils.catchup(node.property.range[1], state);
  utils.append('"]', state);
  return false;
}

visitMemberExpression.test = function(node) {
  return node.type === Syntax.MemberExpression &&
    node.property.type === Syntax.Identifier &&
    reserverdWordsHelper.isES3ReservedWord(node.property.name);
};

exports.visitorList = [
  visitProperty,
  visitMemberExpression
];
