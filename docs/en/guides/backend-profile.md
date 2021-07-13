# Thread dump merging mechanism
The performance profile is an enhancement feature in the APM system. We are using the thread dump to estimate the method execution time, rather than adding multiple local spans. In this way, the resource cost would be much less than using distributed tracing to locate slow method. This feature is suitable in the production environment. This document introduces how thread dumps are merged into the final report as a stack tree(s).

## Thread analyst
### Read data and transform
Read the data from the database and convert it to a data structure in gRPC.
```
st=>start: Start
e=>end: End
op1=>operation: Load data using paging
op2=>operation: Transform data using parallel

st(right)->op1(right)->op2
op2(right)->e
```
Copy the code and paste it into this [link](http://flowchart.js.org/) to generate flow chart.
1. Use the stream to read data by page (50 records per page).
2. Convert the data into gRPC data structures in the form of parallel streams.
3. Merge into a list of data.
### Data analysis
Use the group-by and collector modes in the Java parallel stream to group according to the first stack element in the database records,
and use the collector to perform data aggregation. Generate a multi-root tree.
```
st=>start: Start
e=>end: End
op1=>operation: Group by first stack element
sup=>operation: Generate empty stack tree
acc=>operation: Accumulator data to stack tree
com=>operation: Combine stack trees
fin=>operation: Calculate durations and build result

st(right)->op1->sup(right)->acc
acc(right)->com(right)->fin->e
```
Copy the code and paste it into this [link](http://flowchart.js.org/) to generate a flow chart.
- **Group by first stack element**: Use the first level element in each stack to group, ensuring that the stacks have the same root node.
- **Generate empty stack tree**: Generate multiple top-level empty trees to prepare for the following steps.
The reason for generating multiple top-level trees is that original data can be added in parallel without generating locks.
- **Accumulator data to stack tree**: Add every thread dump into the generated trees.
    1. Iterate through each element in the thread dump to find if there is any child element with the same code signature and same stack depth in the parent element. 
    If not, add this element.
    2. Keep the dump sequences and timestamps in each nodes from the source.
- **Combine stack trees**: Combine all trees structures into one by using the same rules as the `Accumulator`.
    1. Use LDR to traverse the tree node. Use the `Stack` data structure to avoid recursive calls. Each stack element represents the node that needs to be merged.
    2. The task of merging two nodes is to merge the list of children nodes. If they have the same code signature and same parents, save the dump sequences and timestamps in this node. Otherwise, the node needs to be added into the target node as a new child.
- **Calculate durations and build result**: Calculate relevant statistics and generate response.
    1. Use the same traversal node logic as in the `Combine stack trees` step. Convert to a GraphQL data structure, and put all nodes into a list for subsequent duration calculations.
    2. Calculate each node's duration in parallel. For each node, sort the sequences. If there are two continuous sequences, the duration should add the duration of these two seq's timestamp.
    3. Calculate each node execution in parallel. For each node, the duration of the current node should deduct the time consumed by all children.

## Profile data debuggiing
Please follow the [exporter tool](backend-profile-export.md#export-command-line-usage) to package profile data. Unzip the profile data and use [analyzer main function](../../../oap-server/server-tools/profile-exporter/tool-profile-snapshot-bootstrap/src/test/java/org/apache/skywalking/oap/server/tool/profile/exporter/ProfileExportedAnalyze.java) to run it.
