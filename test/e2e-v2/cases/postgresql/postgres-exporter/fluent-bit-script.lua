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

    res = {}

    res["time"] = os.time() * 1000

    res["layer"] = "POSTGRESQL"
    record["layer"] = "POSTGRESQL"


    res["statement"] = log:match("statement: (.+)", statementIndex)
    res["statement"] = string.gsub(res["statement"], '"', '\\"')


    local durationString = log:match("duration: (%d+.%d+) ms", durationIndex)
    res["query_time"] = math.floor(tonumber(durationString))

    record["service"]="postgresql::postgres:5432"
    res["service"]= "postgresql::postgres:5432"

    res["id"] = uuid()

    jsonstr = table2json(res)
    record["body"]={json={}}
    record["body"]["json"]["json"] = jsonstr
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

