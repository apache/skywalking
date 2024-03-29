# Why is Clickhouse or Loki or xxx not supported as a storage option?

## Background

In the past several years, community users have asked why Clickhouse, Loki, or some other storage is not supported in the upstream. We have repeated the answer many times, but it is still happening, at here, I would like to write down the summary to help people understand more

## Previous Discussions
All the following issues were about discussing new storage extension topics.
- Loki as storage
    -  https://github.com/apache/skywalking/discussions/9836
- ClickHouse
    - https://github.com/apache/skywalking/issues/11924
    - https://github.com/apache/skywalking/discussions/9011
- Vertica
    - https://github.com/apache/skywalking/discussions/8817

Generally, all those asking are about adding a new kind of storage.

## Why they don't exist ?
First of all, `WHY` is not a suitable question. SkyWalking is a volunteer-driven community, the volunteers build this project including bug fixes, maintenance work, and new features from their personal and employer interests. What you saw about the current status is the combination of all those interests rather than responsibilities.
So, in SkyWalking, anything you saw existing is/was someone's interest and contributed to upstream.

This logic is the same as this question, SkyWalking active maintainers are focusing on JDBC(MySQL and PostgreSQL ecosystem) Database and Elasticsearch for existing users, and moving forward on BanyanDB as the native one. We for now don't have people interested in ClickHouse or any other database. That is why they are not there.

## How could add one?
To add a new feature, including a new storage plugin, you should go through [SWIP - SkyWalking Improvement Proposal](https://skywalking.apache.org/docs/main/next/en/swip/readme/) workflow, and have a full discussion with the maintenance team.
SkyWalking has a pluggable storage system, so, ideally new storage option is possible to implement a new provider for the storage module. Meanwhile, in practice, as storage implementation should be in high performance and well optimized, considering our experiences with JDBC and Elasticsearch implementations, some flags and annotations may need to be added in the kernel level and data model declarations.

Furthermore, as current maintainers are not a fun of Clickhouse or others(otherwise, you should have seen those implementations), they are not going to be involved in the code implementations and they don't know much more from a general perspective about which kind of implementation in that specific database will have a better behavior and performance. So, if you want to propose this to upstream, you should be very experienced in that database, and have enough scale and environments to provide solid benchmark.

## What happens next if the new implementation gets accepted/merged/released?
Who proposed this new implementation(such as clickhouse storage), has to take the responsibilities of the maintenance. The maintenance means they need to
1. Join storage relative discussion to make sure SkyWalking can move forward on a kernel-level optimization without being blocked by these specific storage options.
2. Respond to this storage relative questions, bugs, CVEs, and performance issues.
3. Make the implementation performance match the expectation of the original proposal. Such as, about clickhouse, people are talking about how they are faster and have higher efficiency than Elasticsearch for large-scale deployments. Then we should always be able to see it has better benchmark and product side practice.

Even if the storage gets accepted/merged/released, but **no one can't take the above responsibilities** or **the community doesn't receive the feedback and questions about those storages**, SkyWalking PMC(Project Management Committee) will start the process to remove the implementations. This happened before for Apache IoTDB and InfluxDB storage options. Here is the last vote about this,
- https://github.com/apache/skywalking/discussions/9059