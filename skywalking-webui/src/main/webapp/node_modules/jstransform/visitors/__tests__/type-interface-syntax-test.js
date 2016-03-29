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

describe('static type interface syntax', function() {
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
      code.join('\n')
    ).code;

    if (visitors) {
      code = jstransform.transform(
        visitors,
        code
      ).code;
    }
    return code;
  }

  describe('Interface Declaration', () => {
    it('strips interface declarations', () => {
      /*global interface*/
      var code = transform([
        'var interface = 42;',
        'interface A { foo: () => number; }',
        'if (true) interface += 42;',
        'interface A<T> extends B, C<T> { foo: () => number; }',
        'interface += 42;'
      ]);
      eval(code);
      expect(interface).toBe(126);
    });

    it('catches up correctly', () => {
      var code = transform([
        "var X = require('X');",
        'interface A { foo: () => number; }',
      ]);
      expect(code).toBe([
        "var X = require('X');",
        '                                  '
      ].join('\n'));
    });
  });
});
