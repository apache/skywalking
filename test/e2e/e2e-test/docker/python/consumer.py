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

import urllib.parse
from urllib import request

from skywalking import agent, config

if __name__ == '__main__':
    config.service_name = 'consumer'
    config.logging_level = 'DEBUG'
    config.protocol = 'http'
    config.collector_address = 'http://oap:12800'
    agent.start()

    import socketserver
    from http.server import BaseHTTPRequestHandler

    class SimpleHTTPRequestHandler(BaseHTTPRequestHandler):
        def do_POST(self):
            self.send_response(200)
            self.send_header('Content-Type', 'application/json; charset=utf-8')
            self.end_headers()

            data = '{"name": "whatever"}'.encode('utf8')
            req = request.Request('http://medium:9092/users')
            req.add_header('Content-Type', 'application/json; charset=utf-8')
            req.add_header('Content-Length', str(len(data)))
            with request.urlopen(req, data):
                self.wfile.write(data)

            req2 = request.Request("http://provider-kafka:9089/users")
            req2.add_header('Content-Type', 'application/json; charset=utf-8')
            req2.add_header('Content-Length', str(len(data)))
            with request.urlopen(req2, data):
                self.wfile.write(data)

    PORT = 9090
    Handler = SimpleHTTPRequestHandler

    with socketserver.TCPServer(("", PORT), Handler) as httpd:
        print("serving at port", PORT)
        httpd.serve_forever()
