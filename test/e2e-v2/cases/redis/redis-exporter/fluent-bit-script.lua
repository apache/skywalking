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

-- The following is an example of a simple Redis slow log
--     102 1684379526 2691 flushall 192.168.150.29:42904
function rewrite_body(tag, timestamp, record)
  local log = record.log
  if isStringOnlyWhitespace(log) then
    return -1, timestamp, record
  end

  if log:sub(1, 1) == "#" then
    return -1, timestamp, record
  end
  record.log = nil
  record.tags = { data = { { key = "LOG_KIND", value = "SLOW_SQL" } } }

  local trimmed = trim(log)
  local splitResult = split(trimmed, " ")

  inner_record = {}

  inner_record.time = os.time() * 1000
  inner_record.layer = "REDIS"

  record.layer = "REDIS"

  -- Here simply set the service name to `root[root] for e2e testing, you can also just set ip:port to service name
  record.service = "redis::" .. "root[root]"
  inner_record.service = "redis::" .. "root[root]"

  local query_time = splitResult[3]
  local qt = math.floor(query_time)
  inner_record.query_time = qt
  inner_record.statement = ""

  inner_record.id = splitResult[1]


  for i = 4, #splitResult - 1 do
    inner_record.statement = inner_record.statement .. splitResult[i]
    inner_record.statement = inner_record.statement .. " "
  end
  inner_record.statement = trim(inner_record.statement)
  inner_record.statement = string.gsub(inner_record.statement, '"', '\\"')

  local jsonstr = table2json(inner_record)
  record.body = { json = { json = jsonstr } }

  return 1, timestamp, record
end

function trim(str)
  return str:gsub("^%s*(.-)%s*$", "%1")
end

function split(str, delimiter)
  local result = {}
  for match in (str..delimiter):gmatch("(.-)"..delimiter) do
    table.insert(result, match)
  end
  return result
end
function isStringOnlyWhitespace(str)
  return str:match("^%s*$") ~= nil
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

