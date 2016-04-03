/**
 * Copyright 2013 Facebook, Inc.
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
 */

/*jshint evil:true*/

require('mock-modules').autoMockOff();

describe('static type syntax syntax', function() {
  var flowSyntaxVisitors;
  var jstransform;

  beforeEach(function() {
    require('mock-modules').dumpCache();

    flowSyntaxVisitors = require('../type-syntax.js').visitorList;
    jstransform = require('jstransform');
  });

  function transform(code, visitors) {
    code = jstransform.transform(
      flowSyntaxVisitors,
      code.join('\n'),
      {sourceType: 'nonStrictModule'}
    ).code;

    if (visitors) {
      code = jstransform.transform(
        visitors,
        code
      ).code;
    }

    return code;
  }

  describe('type alias', () => {
    it('strips type aliases', () => {
      /*global type*/
      /*global sanityCheck*/
      var code = transform([
        'var type = 42;',
        'type FBID = number;',
        'type type = string',
        'type += 42;'
      ]);
      eval(code);
      expect(type).toBe(84);
    });

    it('strips import-type declarations', () => {
      var code = transform([
        'import type DefaultExport from "MyModule";',
        'var sanityCheck = 42;',
      ]);
      eval(code);
      expect(sanityCheck).toBe(42);
    });

    it('catches up correctly', () => {
      var code = transform([
        "var X = require('X');",
        'type FBID = number;',
      ]);
      expect(code).toBe([
        "var X = require('X');",
        '                   '
      ].join('\n'));
    });
  });
});
