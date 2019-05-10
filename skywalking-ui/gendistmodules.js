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

const fs = require('fs');
const rimraf = require('rimraf');

const dir = './ui-license';
if (!fs.existsSync(dir)) {
  fs.mkdirSync(dir);
}
rimraf.sync(`${dir}/*`);

const modules = JSON.parse(fs.readFileSync('modules.json', 'utf8'));
const moduleMap = new Map();
const setMap = (m) => {
  const key = m.alias ? m.alias : m.label;
  if (!moduleMap.has(key)) {
    moduleMap.set(key, { label: key, path: m.path });
  }
};
modules.forEach((element) => {
  const { groups } = element;
  groups.forEach((group) => {
    if (group.label === 'node_modules') {
      group.groups.forEach((m) => {
        if (m.label.startsWith('@')) {
          const parentName = m.label;
          m.groups.forEach(sub => setMap({ label: `${parentName}/${sub.label}`, path: sub.path, alias: `${parentName.slice(1)}-${sub.label}` }));
        } else {
          setMap(m);
        }
      });
    }
  });
});


const crawler = require('npm-license-crawler');
const request = require('request');

const options = {
  start: ['./'],
  dependencies: true,
  omitVersion: false,
};

const fetchLicense = (moduleList) => {
  const m = moduleList.shift();
  if (!m) {
    return;
  }
  if (lackLicenseFile(m)) {
    // eslint-disable-next-line
    console.log(`${m.name}, ${m.licenseUrl} ${m.repository}`);
    fetchLicense(moduleList);
    return;
  }
  request({ rejectUnauthorized: false, url: m.licenseUrl })
    .on('response', (response) => {
      if (response.statusCode !== 200) {
        // eslint-disable-next-line
        console.log(`${m.name}, ${m.licenseUrl} ${response.statusCode}`);
      }
      fetchLicense(moduleList);
    })
    .on('error', (err) => {
      // eslint-disable-next-line
      console.log(`${m.name} error, ${m.licenseUrl} ${err.code}`);
      fetchLicense(moduleList);
    })
    .pipe(fs.createWriteStream(`${dir}/LICENSE-${m.name}`));
};

const lackLicenseFile = m => m.licenseUrl === m.repository;

crawler.dumpLicenses(options,
  (error, res) => {
    if (error) {
      // eslint-disable-next-line
      console.error(`Error:${error}`);
      return;
    }
    const mappedRes = {};
    Object.keys(res).forEach((k) => {
      const labelArray = k.split('@');
      mappedRes[labelArray[0]] = { ...res[k], version: labelArray[1] };
    });
    // eslint-disable-next-line
    console.log(`${moduleMap.size} modules to find license.`);
    const result = [];
    const licenseMap = new Map();
    for (const key of moduleMap.keys()) {
      if (!mappedRes[key]) {
        // eslint-disable-next-line
        console.error(`There is no license for module ${key}`);
      } else {
        const m = { ...mappedRes[key], name: key };
        const list = licenseMap.has(m.licenses) ? licenseMap.get(m.licenses) : [];
        list.push(m);
        licenseMap.set(m.licenses, list);
        result.push(m);
      }
    }

    for (const [licenses, sameLicensesModules] of licenseMap) {
      console.log(licenses);
      sameLicensesModules.sort((a, b) => {
        const nameA = a.name.toUpperCase();
        const nameB = b.name.toUpperCase();
        if (nameA < nameB) {
          return -1;
        }
        if (nameA > nameB) {
          return 1;
        }
        return 0;
      }).forEach(_ =>
        console.log(`${_.name}\t${_.version}:\t${_.repository}\t${_.licenses}${lackLicenseFile(_) ? '\t, declare license in package.json only.' : ''}`));
      console.log('-----------------------------');
    }


    // eslint-disable-next-line
    console.log(`${result.length} modules haven found license`);
    fetchLicense([...result]);
  }
);
