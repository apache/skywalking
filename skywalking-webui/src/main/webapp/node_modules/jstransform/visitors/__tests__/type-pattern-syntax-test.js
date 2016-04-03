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
/*jshint -W117*/

require('mock-modules').autoMockOff();

describe('static type pattern syntax', function() {
  var flowSyntaxVisitors;
  var jstransform;

  beforeEach(function() {
    require('mock-modules').dumpCache();

    flowSyntaxVisitors = require('../type-syntax.js').visitorList;
    jstransform = require('jstransform');
    destructuringVisitors =
      require('../es6-destructuring-visitors');

    visitorList = destructuringVisitors.visitorList;
  });

  function transform(code, visitors) {
    visitors = visitors ? visitorList.concat(visitors) : visitorList;

    // We run the flow transform first
    code = jstransform.transform(
      flowSyntaxVisitors,
      code.join('\n')
    ).code;

    code = jstransform.transform(
      visitors,
      code
    ).code;

    return code;
  }

  describe('Object Pattern', () => {
    it('strips function argument type annotation', () => {
      var code = transform([
        'function foo({x, y}: {x: number; y: number}) { return x+y; }',
        'var thirty = foo({x: 10, y: 20});'
      ]);
      eval(code);
      expect(thirty).toBe(30);
    });
    it('strips var declaration type annotation', () => {
      var code = transform([
        'var {x, y}: {x: number; y: string} = { x: 42, y: "hello" };'
      ]);
      eval(code);
      expect(x).toBe(42);
      expect(y).toBe("hello");
    });
  });

  describe('Array Pattern', () => {
    it('strips function argument type annotation', () => {
      var code = transform([
        'function foo([x, y]: Array<number>) { return x+y; }',
        'var thirty = foo([10, 20]);'
      ]);
      eval(code);
      expect(thirty).toBe(30);
    });
    it('strips var declaration type annotation', () => {
      var code = transform([
        'var [x, y]: Array<number> = [42, "hello"];'
      ]);
      eval(code);
      expect(x).toBe(42);
      expect(y).toBe("hello");
    });
  });
});
