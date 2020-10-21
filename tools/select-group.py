#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

import os
import re
from os import path
from os.path import dirname

import yaml


def main():
    root_dir = path.realpath(path.join(dirname(__file__), '..'))

    group_count = {}
    for root, sub_dirs, files in os.walk(root_dir):
        for filename in files:
            if re.match('plugins-test\\.\\d+\\.ya?ml', filename):
                count_plugin_tests(filename, group_count, root_dir)

    group_count = sorted(group_count.items(), key=lambda x: x[1])
    for gc in group_count:
        print(gc[0], gc[1])


def count_plugin_tests(filename, group_count, root_dir):
    yaml_file = path.join(root_dir, '.github/workflows/', filename)
    content = yaml.safe_load(open(yaml_file))
    group_count[yaml_file] = 0
    for job in content['jobs'].values():
        if 'strategy' in job and 'matrix' in job['strategy'] and 'case' in job['strategy']['matrix']:
            cases = job['strategy']['matrix']['case']
            for case in cases:
                count_scenario_cases(case, group_count, root_dir, yaml_file)


def count_scenario_cases(case, group_count, root_dir, yaml_file):
    version_filename = path.join(root_dir, 'test/plugin/scenarios', case, 'support-version.list')
    with open(version_filename) as version_file:
        for line in version_file.readlines():
            if not line.startswith('#') and len(line.strip()) > 0:
                group_count[yaml_file] += 1


if __name__ == '__main__':
    main()
