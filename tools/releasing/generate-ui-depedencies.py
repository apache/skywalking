#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Use npm "npm-license-crawler" to generate dependencies, notice: don't include dev dependencies
# > npm-license-crawler --dependencies --relativeLicensePath --csv license.csv
# run this script to generate the license and ui-licenses folder

import csv, os, shutil

def copyLicense(srcfile, moduleName):
    srcfile = srcfile.replace("\\", "/")
    if not os.path.exists('ui-licenses'):
        os.makedirs('ui-licenses')
    shutil.copyfile('../../skywalking-ui/' + srcfile, 'ui-licenses/LICENSE-' + moduleName.replace("/", "-") + ".txt")

with open('license-file-section.csv', 'wb') as csvfile:
    fileWriter = csv.writer(csvfile, delimiter=',',
                            quotechar='|', quoting=csv.QUOTE_MINIMAL)
    with open('licenses.csv') as csvfile:
        reader = csv.DictReader(csvfile)
        for row in reader:
            license = row['licenses']
            combinedName = row['module name']
            repoUrl = row['repository']
            if combinedName.startswith('@'):
                combinedName = combinedName[1:]
                moduleName = combinedName
            verPos = combinedName.rfind("@")
            moduleName = combinedName[:verPos]
            verPos += 1
            version = combinedName[verPos:]

            # Copy the license file to temp folder
            licenseFile = row['licenseFile']
            if licenseFile != '':
                fileWriter.writerow([moduleName, version + ':', repoUrl, license]);
                copyLicense(licenseFile, moduleName)
            else:
                fileWriter.writerow([moduleName, version + ':', repoUrl, license, "declare license in package.json only."]);