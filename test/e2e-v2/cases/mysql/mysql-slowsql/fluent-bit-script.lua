--
-- Licensed to the Apache Software Foundation (ASF) under one or more
-- contributor license agreements.  See the NOTICE file distributed with
-- this work for additional information regarding copyright ownership.
-- The ASF licenses this file to You under the Apache License, Version 2.0
-- (the "License"); you may not use this file except in compliance with
-- the License.  You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--

-- The following is an example of a simple MySQL slow log
--   # Time: 2022-12-06T15:11:00.449826Z
--   # User@Host: root[root] @  [127.0.0.1]  Id:    29
--   # Query_time: 11.000312  Lock_time: 0.000000 Rows_sent: 1  Rows_examined: 0
--   SET timestamp=1670339460;
--   SELECT SLEEP(11);
function rewrite_body(tag, timestamp, record)
  local log = record.log
  record.log = nil
  record.tags = { data = { { key = "LOG_KIND", value = "SLOW_SQL" } } }

  local lines = {}
  for line in string.gmatch(log, "[^\n]+") do
    table.insert(lines, line)
  end
  inner_record = {}

  inner_record.time = os.time() * 1000
  inner_record.layer = "MYSQL"

  record.layer = "MYSQL"

  -- Here simply set the service name to `root[root]`, you can also get the part you want from the second line of the log as the service name
  record.service = "mysql::" .. "root[root]"
  inner_record.service = "mysql::" .. "root[root]"

  local query_time = lines[3]:match("%s(%S+)%s+Lock")
  local qt = math.floor(query_time * 1000)
  inner_record.query_time = qt
  inner_record.statement = ""

  inner_record.id = uuid()

  for i = 4, #lines, 1 do
    inner_record.statement = inner_record.statement .. lines[i]
  end
  inner_record.statement = string.gsub(inner_record.statement, '"', '\\"')

  local jsonstr = table2json(inner_record)
  record.body = { json = { json = jsonstr } }

  return 1, timestamp, record
end

local UUID_TEMPLATE = '%s-%s-%s-%s-%s'

local random_hex = {}

function random_hex.generate(n)
  local seed = {'0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f'}
  local s = ""
  for i = 1, n do
    s = s .. seed[math.random(1, 16)]
  end
  return s
end

function uuid(seed)
  if seed then
    math.randomseed(seed)
  else
    math.randomseed(os.time())
  end
  local sid = string.format(UUID_TEMPLATE,
          random_hex.generate(4), random_hex.generate(4), random_hex.generate(4),
          random_hex.generate(4), random_hex.generate(8)
  )
  return sid
end

function table2json(t)
  local function serialize(tbl, structure)
    local tmp = {}
    for k, v in pairs(tbl) do
      local k_type = type(k)
      local v_type = type(v)
      local key
      if k_type == "string" then
        key = '"' .. k .. '":'
      elseif k_type == "number" then
        key = ""
        if structure == "array" then
          key = ""
        else
          key = '"' .. k .. '":'
        end
      else
        key = '"' .. tostring(k) .. '":'
      end
      local value
      if v_type == "table" then
        if next(v) == nil then
          value = "null"
        else
          value = serialize(v, structure)
        end
      elseif v_type == "boolean" then
        value = tostring(v)
      elseif v_type == "string" then
        value = '"' .. v .. '"'
      else
        value = v
      end
      tmp[#tmp + 1] = key and value and tostring(key) .. tostring(value) or nil
    end
    if structure == "array" then
      return "[" .. table.concat(tmp, ",") .. "]"
    else
      return "{" .. table.concat(tmp, ",") .. "}"
    end
  end
  assert(type(t) == "table")
  if next(t) == nil then
    return "null"
  elseif next(t, next(t)) == nil and type(next(t)) == "number" then
    return serialize(t, "array")
  else
    return serialize(t, "table")
  end
end

