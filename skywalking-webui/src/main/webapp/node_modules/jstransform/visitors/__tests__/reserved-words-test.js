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
 * @emails jeffmo@fb.com
 */

/*jshint evil:true*/

jest.autoMockOff();

describe('reserved-words', function() {
  var transformFn;
  var visitors;

  beforeEach(function() {
    require('mock-modules').dumpCache();
    visitors = require('../reserved-words-visitors').visitorList;
    transformFn = require('../../src/jstransform').transform;
  });

  function transform(code, opts) {
    // No need for visitors as long as we are not in es5 mode.
    return transformFn(visitors, code, opts).code;
  }

  describe('reserved words in member expressions', function() {
    it('should transform to reserved word members to computed', function() {
      var code = 'foo.delete;';

      expect(transform(code)).toEqual('foo["delete"];');

      code = '(foo++).delete;';
      expect(transform(code)).toEqual('(foo++)["delete"];');
    });

    it('should handle call expressions', function() {
      var code = 'foo.return();';

      expect(transform(code)).toEqual('foo["return"]();');
    });

    it('should only quote ES3 reserved words', function() {
      var code = 'foo.await();';

      expect(transform(code)).toEqual('foo.await();');
    });
  });

  describe('reserved words in properties', function() {
    it('should quote reserved words in properties', function() {
      var code = 'var x = {null: 1};';

      expect(transform(code)).toEqual('var x = {"null": 1};');
    });

    it('should only quote ES3 reserved words', function() {
      var code = 'var x = {await: 1};';

      expect(transform(code)).toEqual('var x = {await: 1};');
    });
  });
});
