-- Apache Doris DDL Script for Apache SkyWalking

-- Note: This script provides a basic starting point.
-- For production environments, consider:
-- 1. Replication: Ensure appropriate `replication_num` property is set for your tables or system.
-- 2. Partitioning: For time-series data (metrics, segments, logs), partitioning by time (e.g., by day or week on `time_bucket` or `timestamp`) is highly recommended for performance and data lifecycle management.
-- 3. Bucketing: Adjust bucket numbers based on your cluster size and data volume.
-- 4. Data Types: Review VARCHAR lengths and numeric types for your specific needs.
-- 5. Key Models: Choose between DUPLICATE KEY, UNIQUE KEY, AGGREGATE KEY based on table characteristics and query patterns.

-- ----------------------------
-- Table structure for metrics_all
-- This is a generic table for various metrics. `metric_name` distinguishes them.
-- Consider if specific tables per metric type might be better for very high volume.
-- ----------------------------
CREATE TABLE IF NOT EXISTS `metrics_all` (
    `id` VARCHAR(255) NOT NULL COMMENT 'Unique ID for the metric data point, often a composite of entity ID and time',
    `metric_name` VARCHAR(255) NOT NULL COMMENT 'Name of the metric (e.g., service_cpm, endpoint_p99)',
    `entity_id` VARCHAR(255) NOT NULL COMMENT 'ID of the entity this metric pertains to (e.g., service_id, instance_id, endpoint_id)',
    `value` BIGINT COMMENT 'Metric value. Use DOUBLE for floating-point metrics.',
    `time_bucket` BIGINT NOT NULL COMMENT 'Time bucket (e.g., YYYYMMDDHHMM)',
    `timestamp` BIGINT NOT NULL COMMENT 'Timestamp in milliseconds (epoch)'
)
DUPLICATE KEY(`id`, `metric_name`, `time_bucket`)
DISTRIBUTED BY HASH(`id`) BUCKETS 16 -- Adjust bucket number as needed
PROPERTIES (
    "replication_allocation" = "tag.location.default: 1" -- Adjust replication as needed
);
-- Example index for common queries:
-- CREATE INDEX idx_metrics_query ON metrics_all (metric_name, entity_id, time_bucket);

-- Note on labels for metrics:
-- If metrics can have arbitrary labels, consider:
-- 1. A `labels` TEXT column storing JSON: `labels` TEXT COMMENT 'JSON string of key-value labels'
-- 2. Dedicated columns for common labels: `label_key1` VARCHAR(255), `label_value1` VARCHAR(255)
-- Querying JSON is less performant than dedicated columns.

-- ----------------------------
-- Table structure for segment
-- Stores trace segment data.
-- ----------------------------
CREATE TABLE IF NOT EXISTS `segment` (
    `trace_id` VARCHAR(255) NOT NULL COMMENT 'Trace ID',
    `segment_id` VARCHAR(255) NOT NULL COMMENT 'Segment ID, primary key for this record',
    `service_id` VARCHAR(255) NOT NULL COMMENT 'Service ID',
    `service_instance_id` VARCHAR(255) NOT NULL COMMENT 'Service Instance ID',
    `endpoint_id` VARCHAR(255) COMMENT 'Endpoint ID or name',
    `start_time` BIGINT NOT NULL COMMENT 'Start time of the segment in milliseconds (epoch)',
    `end_time` BIGINT NOT NULL COMMENT 'End time of the segment in milliseconds (epoch)',
    `duration` INT NOT NULL COMMENT 'Duration of the segment in milliseconds',
    `is_error` TINYINT NOT NULL COMMENT 'Boolean flag (0 or 1) indicating if the segment has an error',
    `data_binary` TEXT COMMENT 'Serialized SegmentObject (e.g., JSON string)',
    `version` INT COMMENT 'Protocol version',
    `tags` TEXT COMMENT 'JSON array of KeyValue tags',
    `time_bucket` BIGINT NOT NULL COMMENT 'Time bucket of the segment start time (e.g., YYYYMMDDHHMM)'
)
UNIQUE KEY(`segment_id`)
DISTRIBUTED BY HASH(`segment_id`) BUCKETS 16
PROPERTIES (
    "replication_allocation" = "tag.location.default: 1"
);
-- Example indexes for common queries:
-- CREATE INDEX idx_segment_trace_id ON segment (trace_id);
-- CREATE INDEX idx_segment_service_start_time ON segment (service_id, start_time);
-- CREATE INDEX idx_segment_time_bucket ON segment (time_bucket);


-- ----------------------------
-- Table structure for log_record
-- Stores log data.
-- ----------------------------
CREATE TABLE IF NOT EXISTS `log_record` (
    `id` VARCHAR(255) NOT NULL COMMENT 'Unique ID for the log entry, often related to segment/span and timestamp',
    `service_id` VARCHAR(255) COMMENT 'Service ID',
    `service_instance_id` VARCHAR(255) COMMENT 'Service Instance ID',
    `endpoint_id` VARCHAR(255) COMMENT 'Endpoint ID or name',
    `trace_id` VARCHAR(255) COMMENT 'Trace ID, if the log is part of a trace',
    `segment_id` VARCHAR(255) COMMENT 'Segment ID, if the log is part of a trace segment',
    `timestamp` BIGINT NOT NULL COMMENT 'Timestamp of the log in milliseconds (epoch)',
    `content_type` INT COMMENT 'Type of content (e.g., 1=TEXT, 2=JSON, 3=YAML)',
    `content` TEXT COMMENT 'Log content',
    `tags_raw_data` TEXT COMMENT 'JSON string for tags associated with the log',
    `time_bucket` BIGINT NOT NULL COMMENT 'Time bucket of the log timestamp (e.g., YYYYMMDDHHMM)'
)
UNIQUE KEY(`id`)
DISTRIBUTED BY HASH(`id`) BUCKETS 16
PROPERTIES (
    "replication_allocation" = "tag.location.default: 1"
);
-- Example indexes:
-- CREATE INDEX idx_log_trace_id ON log_record (trace_id);
-- CREATE INDEX idx_log_service_timestamp ON log_record (service_id, timestamp);
-- CREATE INDEX idx_log_time_bucket ON log_record (time_bucket);

-- ----------------------------
-- Table structure for alarm_record
-- Stores alarm data.
-- ----------------------------
CREATE TABLE IF NOT EXISTS `alarm_record` (
    `id` VARCHAR(255) NOT NULL COMMENT 'Unique ID for the alarm record',
    `scope_id` INT COMMENT 'Scope ID (e.g., Service, Instance, Endpoint)',
    `scope` VARCHAR(255) COMMENT 'Name of the scope (e.g., SERVICE, SERVICE_INSTANCE)',
    `name` VARCHAR(255) COMMENT 'Name of the alarm rule or metric',
    `rule_name` VARCHAR(255) COMMENT 'The specific rule that was triggered',
    `start_time` BIGINT NOT NULL COMMENT 'Start time of the alarm period in milliseconds (epoch)',
    `alarm_message` TEXT COMMENT 'Detailed alarm message',
    `tags_raw_data` TEXT COMMENT 'JSON string for tags associated with the alarm',
    `time_bucket` BIGINT NOT NULL COMMENT 'Time bucket of the alarm start_time (e.g., YYYYMMDDHHMM)'
)
UNIQUE KEY(`id`)
DISTRIBUTED BY HASH(`id`) BUCKETS 16
PROPERTIES (
    "replication_allocation" = "tag.location.default: 1"
);
-- Example indexes:
-- CREATE INDEX idx_alarm_scope_rule_time ON alarm_record (scope_id, rule_name, start_time);
-- CREATE INDEX idx_alarm_time_bucket ON alarm_record (time_bucket);

-- Reminder: After creating tables, you might want to create additional materialized views (rollups)
-- in Doris for aggregated metrics to speed up queries, depending on your specific query patterns.
-- Example:
-- CREATE MATERIALIZED VIEW service_cpm_hourly AS
-- SELECT
--   metric_name,
--   entity_id,
--   CAST(timestamp / (1000 * 60 * 60) AS BIGINT) * (1000 * 60 * 60) AS hour_timestamp, -- hourly aggregation
--   SUM(value) AS total_value,
--   COUNT(*) AS count_value
-- FROM metrics_all
-- WHERE metric_name = 'service_cpm'
-- GROUP BY metric_name, entity_id, hour_timestamp;

-- Note on `trace_state` in `segment` table:
-- The prompt mentioned `trace_state` (INT) but `is_error` (TINYINT) is more common in SkyWalking's segment storage.
-- I've used `is_error`. If `trace_state` is needed with more states, the DDL should be adjusted.

-- Note on `time_bucket` for `segment` and `log_record`:
-- Added `time_bucket` to these tables as it's a common SkyWalking pattern for partitioning and TTL.
-- It should be derived from `start_time` (for segments) or `timestamp` (for logs)
-- during data insertion by the OAP server. Example: YYYYMMDDHHMM for hourly buckets.
-- If `time_bucket` is not populated by OAP, then queries on it won't work, and partitioning based on it
-- would require it to be populated. It's crucial for time-series data management.
-- For `metrics_all`, `time_bucket` is a primary part of how metrics are stored.
-- Example: `time_bucket` for `metrics_all` might be `202301011230` (YYYYMMDDHHMM).
-- Example: `time_bucket` for `segment` might be `2023010112` (YYYYMMDDHH).
-- Example: `time_bucket` for `log_record` might be `2023010112` (YYYYMMDDHH).
-- The exact format/granularity of `time_bucket` needs to be consistent with what OAP generates.
-- Assuming BIGINT can store YYYYMMDDHHMMSS or similar numeric representations of time windows.

-- The `endpoint_id` in `segment` table: the prompt used VARCHAR(255).
-- SkyWalking often uses an integer ID for endpoints internally and maps it to a string name.
-- If you store integer IDs, change type to INT/BIGINT. If string names, VARCHAR is fine.
-- I've kept it as VARCHAR(255) as per prompt.

-- `tags` in `segment` table: Stored as TEXT, assumed to be a JSON string representation of List<KeyValue>.
-- Example: '[{"key":"http.method","value":"GET"},{"key":"status_code","value":"200"}]'
-- This is consistent with `tags_raw_data` in log/alarm records.
