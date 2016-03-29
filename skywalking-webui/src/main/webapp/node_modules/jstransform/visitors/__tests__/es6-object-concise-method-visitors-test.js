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

/**
 * @emails dmitrys@fb.com
 */

/*jshint evil:true*/

require('mock-modules').autoMockOff();

describe('es6-object-concise-method-visitors', function() {
  var transformFn;
  var conciseMethodVisitors;
  var restParamVisitors;
  var visitors;

  beforeEach(function() {
    require('mock-modules').dumpCache();
    conciseMethodVisitors = require('../es6-object-concise-method-visitors').visitorList;
    restParamVisitors = require('../es6-rest-param-visitors').visitorList;
    transformFn = require('../../src/jstransform').transform;
    visitors = conciseMethodVisitors.concat(restParamVisitors);
  });

  function transform(code) {
    return transformFn(visitors, code).code;
  }

  function expectTransform(code, result) {
    expect(transform(code)).toEqual(result);
  }

  // Functional tests.

  it('should transform concise method and return 42', function() {
    /*global foo*/
    var code = transform([
      'var foo = {',
      '  bar(x) {',
      '    return x;',
      '  }',
      '};'
    ].join('\n'));

    eval(code);
    expect(foo.bar(42)).toEqual(42);
  });

  it('should transform concise method with literal property', function() {
    var code = transform([
      'var foo = {',
      '  "bar 1"(x) {',
      '    return x;',
      '  }',
      '};'
    ].join('\n'));

    eval(code);
    expect(foo['bar 1'](42)).toEqual(42);
  });


  it('should work with rest params', function() {
    var code = transform([
      '({',
      '  init(x, ...rest) {',
      '    return rest.concat(x);',
      '  }',
      '}).init(1, 2, 3);'
    ].join('\n'));

    expect(eval(code)).toEqual([2, 3, 1]);
  });

  // Source code tests.
  it('should transform concise methods', function() {

    // Should transform simple concise method.
    expectTransform(
      'var foo = {bar() {}};',
      'var foo = {bar:function() {}};'
    );

    // Should transform inner objects.
    expectTransform(
      '({bar(x) { return {baz(y) {}}; }});',
      '({bar:function(x) { return {baz:function(y) {}}; }});'
    );
  });

  it('should preserve generators', function() {
    // Identifier properties
    expectTransform(
      'var foo = {*bar(x) {yield x;}};',
      'var foo = {bar:function*(x) {yield x;}};'
    );

    // Literal properties
    expectTransform(
      'var foo = {*"abc"(x) {yield x;}, *42(x) {yield x;}};',
      'var foo = {"abc":function*(x) {yield x;}, 42:function*(x) {yield x;}};'
    );

    // Dynamic properties
    expectTransform(
      'var foo = {*[a+b](x) {yield x;}}',
      'var foo = {[a+b]:function*(x) {yield x;}}'
    );
  });

  it('should handle reserved words', function() {
    expectTransform(
      '({delete(x) {}})',
      '({"delete":function(x) {}})'
    );
  });
});


