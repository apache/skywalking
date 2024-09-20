# SWIP - SkyWalking Improvement Proposal

SWIP - SkyWalking Improvement Proposal, is an official document to propose a new feature and/or feature improvement,
which
are relative to end users and developers.

SkyWalking has been very stable since v9.x. We are getting over the rapid changing stage. The core concepts, protocols for
reporting telemetry and query, 3rd party integration, and the streaming process kernel are very stable. From now(2024) on,
SkyWalking community would focus more on improvement and controllable improvement. All major changes should be evaluated
more seriously, and try as good as possible to avoid incompatible breaking changes.

## What is considered a major change?

The catalogs of a major change are listed as follows

- New Feature. A feature doesn't exist for the latest version.
- Any change of the network Interfaces, especially
  for [Query Protocol](https://github.com/apache/skywalking-query-protocol),
  [Data Collect Protocols](https://github.com/apache/skywalking-data-collect-protocol),
  Dynamic Configuration APIs, Exporting APIs, AI pipeline APIs.
- Any change of storage structure.

**Q: Is Agent side feature or change considered a SWIP?**

A: Right now, SWIP targets OAP and UI side changes. All agent side changes are pending on the reviews from the
committers of those agents.

## SWIP Template

The purpose of this template should not be considered a hard requirement. The major purpose of SWIP is helping the PMC
and community member to understand the proposal better.

```markdown
# Title: SWIP-1234 xxxx

## Motivation 
The description of new feature or improvement.

## Architecture Graph
Describe the relationship between your new proposal part and existing components.

## Proposed Changes
State your proposal in detail.

## Imported Dependencies libs and their licenses.

## Compatibility
Whether breaking configuration, storage structure, or protocols.

## General usage docs
This doesn't have to be a final version, but helps the reviewers to understand how to use this new feature.
```

## SWIP Process

Here is the process for starting a SWIP.

1. Start a SWIP discussion at [GitHub Discussion Page](https://github.com/apache/skywalking/discussions/categories/swip)
   with title `[DISCUSS] xxxx`.
2. Fill in the sections as described above in `SWIP Template`.
3. At least one SkyWalking committer commented on the discussion to show interest in adopting it.
4. This committer could update this page to grant a **SWIP ID**, and update the title to `[SWIP-ID NO.] [DISCUSS] xxxx`.
5. All further discussion could happen on the discussion page.
6. Once the consensus is made by enough committer supporters, and/or through a mail list vote, this SWIP should be
   added [here](./) as `SWIP-ID NO.md` and listed in the below as `Known SWIPs`.

All accepted and proposed SWIPs can be found in [here](https://github.com/apache/skywalking/discussions/categories/swip).

## Known SWIPs

Next SWIP Number: 9

### Accepted SWIPs

- [SWIP-6 Support ActiveMQ Monitoring](SWIP-6)
- [SWIP-5 Support ClickHouse Monitoring](SWIP-5.md)
- [SWIP-4 Support available layers of service in the topology](SWIP-4.md)
- [SWIP-3 Support RocketMQ Monitoring](SWIP-3.md)
- [SWIP-2 Collecting and Gathering Kubernetes Monitoring Data](SWIP-2.md)
- [SWIP-1 Create and detect Service Hierarchy Relationship](SWIP-1.md)
