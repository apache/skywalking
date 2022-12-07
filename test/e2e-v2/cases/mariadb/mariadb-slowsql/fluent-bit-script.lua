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

function rewrite_body(tag, timestamp, record)
  log = record["log"]
  record["log"] = nil
  record["date"] = nil
  record["tags"] = {data={{key="LOG_KIND", value="SLOW_SQL"}}}
  arr = split(log,"\n")
  re1 = {}
  
  time = string.sub(arr[1], 9)
  time = string.sub(time,1,19)
  time = string.gsub(time,"-","");
  time = string.gsub(time,"T","");
  time = string.gsub(time,":","");

  y1 = string.sub(time,1,4)
  m1 = string.sub(time,5,6)
  d1 = string.sub(time,7,8)
  h1 = string.sub(time,9,10)
  min1 = string.sub(time,11,12)
  s1 = string.sub(time,13,14)

  re1["time"] = os.time() * 1000

  re1["layer"] = "MYSQL"
  record["layer"] = "MYSQL"

  service = "mysql::db"
  record["service"]=service
  re1["service"]= service

  f1,_ = string.find(arr[4],"Lock")
  query_time = string.sub(arr[4],15,f1-3)
  local qt,_ = math.modf(query_time*1000)
  re1["query_time"] = qt
  re1["statement"] = ""

  re1["id"] = uuid()

  for i=6,#arr,1 do
      re1["statement"] = re1["statement"]..arr[i]
  end
  jsonstr = table2json(re1)
  record["body"]={json={}}
  record["body"]["json"]["json"] = jsonstr
  return 1, timestamp, record
end
function split(input, delimiter)
  input = tostring(input)
  delimiter = tostring(delimiter)
  if (delimiter == "") then return false end
  local pos, arr = 0, {}
  for st, sp in function() return string.find(input, delimiter, pos, true) end do
      table.insert(arr, string.sub(input, pos, st - 1))
      pos = sp + 1
  end
  table.insert(arr, string.sub(input, pos))
  return arr
end

function uuid()
  local seed={'e','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f'}
  local tb={}
  for i=1,32 do
      table.insert(tb,seed[math.random(1,16)])
  end
  local sid=table.concat(tb)
  return string.format('%s-%s-%s-%s-%s',
      string.sub(sid,1,8),
      string.sub(sid,9,12),
      string.sub(sid,13,16),
      string.sub(sid,17,20),
      string.sub(sid,21,32)
  )
end

function table2json(t)
local function serialize(tbl)
  local tmp = {}
  for k, v in pairs(tbl) do
    local k_type = type(k)
    local v_type = type(v)
    local key = (k_type == "string" and '"' .. k .. '":') or (k_type == "number" and "")
    local value =
      (v_type == "table" and serialize(v)) or (v_type == "boolean" and tostring(v)) or
      (v_type == "string" and '"' .. v .. '"') or
      (v_type == "number" and v)
    tmp[#tmp + 1] = key and value and tostring(key) .. tostring(value) or nil
  end
  if table.maxn(tbl) == 0 then
    return "{" .. table.concat(tmp, ",") .. "}"
  else
    return "[" .. table.concat(tmp, ",") .. "]"
  end
end
assert(type(t) == "table")
return serialize(t)
end