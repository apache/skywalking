# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

#!/bin/bash

set -e

# Number of days the certificates will be valid
DAYS=36500   # 100 years
DOMAIN=banyandb
OUTDIR=tls


pushd .
mkdir -p ${OUTDIR}
cd ${OUTDIR}

echo "Generating the CA..."
openssl genrsa -out ca.key 2048
openssl req -x509 -new -nodes -key ca.key -sha256  -out ca.crt -days ${DAYS} -subj "/C=CN/ST=Test/L=Test/O=SkyWalking"
openssl x509 -in ca.crt -text

echo "Generating the BanyanDB server certificate..."
openssl genrsa -out cert.key 2048
openssl req -new -key cert.key -out cert.csr -subj "/C=CN/ST=Test/L=Test/O=SkyWalking/CN=${DOMAIN}"
openssl x509 -req -extfile <(printf "subjectAltName=DNS:${DOMAIN}") -in cert.csr -CA ca.crt -CAkey ca.key -CAcreateserial -out cert.crt -days ${DAYS} -sha256
#openssl x509 -req -extfile <(printf "subjectAltName=IP:127.0.0.1,DNS:${DOMAIN}") -in cert.csr -CA ca.crt -CAkey ca.key -CAcreateserial -out cert.crt -days ${DAYS} -sha256
#openssl req -x509 -nodes -days 365 -newkey rsa:2048 -keyout key.pem -out cert.pem -subj "/CN=localhost" -extensions san -config <(echo "[req]"; echo distinguished_name=req; echo "[san]"; echo "subjectAltName=IP:127.0.0.1,DNS:localhost")
openssl x509 -in cert.crt -text

echo "Importing the certificates into a Java keystore..."
keytool -keystore KeyStore.jks -alias tetrate -import -file cert.crt -storepass temporal -validity ${DAYS}

popd

echo
echo "CA and certificates have been created."
echo "cert.crt have been imported into KeyStore.jks."
