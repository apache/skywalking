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


import * as React from 'react';
import { RouteProps } from 'react-router';

type authorityFN = (currentAuthority?: string) => boolean;

type authority = string | Array<string> | authorityFN | Promise<any>;

export type IReactComponent<P = any> =
  | React.StatelessComponent<P>
  | React.ComponentClass<P>
  | React.ClassicComponentClass<P>;

interface Secured {
  (authority: authority, error?: React.ReactNode): <T extends IReactComponent>(
    target: T,
  ) => T;
}

export interface AuthorizedRouteProps extends RouteProps {
  authority: authority;
}
export class AuthorizedRoute extends React.Component<
  AuthorizedRouteProps,
  any
> {}

interface check {
  <T extends IReactComponent, S extends IReactComponent>(
    authority: authority,
    target: T,
    Exception: S,
  ): T | S;
}

interface AuthorizedProps {
  authority: authority;
  noMatch?: React.ReactNode;
}

export class Authorized extends React.Component<AuthorizedProps, any> {
  static Secured: Secured;
  static AuthorizedRoute: typeof AuthorizedRoute;
  static check: check;
}

declare function renderAuthorize(currentAuthority: string): typeof Authorized;

export default renderAuthorize;
