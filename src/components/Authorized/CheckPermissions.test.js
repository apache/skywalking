/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import { checkPermissions } from './CheckPermissions.js';

const target = 'ok';
const error = 'error';

describe('test CheckPermissions', () => {
  it('Correct string permission authentication', () => {
    expect(checkPermissions('user', 'user', target, error)).toEqual('ok');
  });
  it('Correct string permission authentication', () => {
    expect(checkPermissions('user', 'NULL', target, error)).toEqual('error');
  });
  it('authority is undefined , return ok', () => {
    expect(checkPermissions(null, 'NULL', target, error)).toEqual('ok');
  });
  it('currentAuthority is undefined , return error', () => {
    expect(checkPermissions('admin', null, target, error)).toEqual('error');
  });
  it('Wrong string permission authentication', () => {
    expect(checkPermissions('admin', 'user', target, error)).toEqual('error');
  });
  it('Correct Array permission authentication', () => {
    expect(checkPermissions(['user', 'admin'], 'user', target, error)).toEqual(
      'ok'
    );
  });
  it('Wrong Array permission authentication,currentAuthority error', () => {
    expect(
      checkPermissions(['user', 'admin'], 'user,admin', target, error)
    ).toEqual('error');
  });
  it('Wrong Array permission authentication', () => {
    expect(checkPermissions(['user', 'admin'], 'guest', target, error)).toEqual(
      'error'
    );
  });
  it('Wrong Function permission authentication', () => {
    expect(checkPermissions(() => false, 'guest', target, error)).toEqual(
      'error'
    );
  });
  it('Correct Function permission authentication', () => {
    expect(checkPermissions(() => true, 'guest', target, error)).toEqual('ok');
  });
});
