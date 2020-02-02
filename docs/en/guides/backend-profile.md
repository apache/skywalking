# Thread dump merging mechanism
The performance profile is an enhancement feature in the APM system. We are using the thread dump to estimate the method execution time, rather than adding many local spans. In this way, the resource cost would be much less than using distributed tracing to locate slow method. This feature is suitable in the production environment. This document introduces how thread dumps are merged into the final report as a stack tree(s).

## Thread analyst
### Read data and transform
Read data from the database and convert it to a data structure in gRPC.
```
st=>start: Start
e=>end: End
op1=>operation: Load data using paging
op2=>operation: Transform data using parallel

st(right)->op1(right)->op2
op2(right)->e
```
Copy code and paste it into this [link](http://flowchart.js.org/) to generate flow chart.
1. Use the stream to read data by page (50 records per page).
2. Convert data into gRPC data structures in the form of parallel streams.
3. Merge into a list of data.
### Data analyze
Use the group by and collector modes in the Java parallel stream to group according to the first stack element in the database records,
and use the collector to perform data aggregation. Generate a multi-root tree.
```
st=>start: Start
e=>end: End
op1=>operation: Group by first stack element
sup=>operation: Generate empty stack tree
acc=>operation: Accumulator data to stack tree
com=>operation: Combine stack trees
fin=>operation: Calculate durations

st(right)->op1->sup(right)->acc
acc(right)->com(right)->fin->e
```
Copy code and paste it into this [link](http://flowchart.js.org/) to generate flow chart.
- **Supplier**: Generate multiple top-level empty trees for preparation of the following steps.
- **Accumulator**: Add every thread dump into the generated trees.
    1. Merge the thread dump when the code signature and stack depth are equal.
    2. Keep the dump sequences and timestamps in each nodes from the source.
- **Combiner**: Combine all trees structures into one by using the rules as same as `Accumulator`.
- **Finisher**: Calculate relevant statistics and generate response.
    1. Convert to a GraphQL data structure, and put all nodes merged in the Combiner into a list for subsequent calculations.
    2. Calculate each node's duration in parallel. For each node, sort the sequences, if there are two continuous sequences, the duration should add the duration of these two seq's timestamp.
    3. Calculate each node execution in parallel. For each node, the duration of the current node should minus the time consumed by all children.
