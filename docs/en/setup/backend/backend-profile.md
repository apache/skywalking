# Profile
The profile is a sniffer line level analysis tool. Add the endpoint of the need profile in the UI interface, and the sniffer will perform the thread stack dump within the specified time. Finally, the existing stack data can be analyzed in the UI interface.
1. [Thread analyst](#thread-analyst). They introduce how to analyze all thread dump and combine them to one stack tree.

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
- **Supplier**: Generate multiple top-level empty tree nodes in preparation for the following steps.
- **Accumulator**: Add each data to the tree.
    1. Traverse each layer in the stack, add related child nodes and add nodes when the node does not exist.
    2. Save the dump time and sequence in the current stack to this node.
- **Combine**: Combine all tree structures generated in the first step into one.
- **Finisher**: Calculate relevant statistics and generate GraphQL data.
    1. Convert to a GraphQL data structure, and put all nodes merged in the Combiner into a list for subsequent calculations.
    2. Calculate each node's duration in parallel: sort all the snapshots in the node according to sequence, and summarize them according to each time pane.
    3. Calculate the execution time of children of each node in parallel: the duration of the current node minus the time consumed by all children.
