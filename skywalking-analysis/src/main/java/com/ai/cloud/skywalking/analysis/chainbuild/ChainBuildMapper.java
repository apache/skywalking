package com.ai.cloud.skywalking.analysis.chainbuild;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.mapreduce.TableMapper;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.io.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ai.cloud.skywalking.analysis.chainbuild.entity.TraceSpanTree;
import com.ai.cloud.skywalking.analysis.chainbuild.util.VersionIdentifier;
import com.ai.cloud.skywalking.analysis.config.ConfigInitializer;
import com.ai.cloud.skywalking.protocol.Span;

public class ChainBuildMapper extends TableMapper<Text, Text> {
	private Logger logger = LoggerFactory
			.getLogger(ChainBuildMapper.class);

	@Override
	protected void setup(Context context) throws IOException,
			InterruptedException {
		ConfigInitializer.initialize();
	}
	
	@Override
	protected void map(ImmutableBytesWritable key, Result value, Context context)
			throws IOException, InterruptedException {
		if(!VersionIdentifier.enableAnaylsis(Bytes.toString(key.get()))){
			return;
		}
		
		try {
			List<Span> spanList = new ArrayList<Span>();
			for (Cell cell : value.rawCells()) {
				Span span = new Span(Bytes.toString(cell.getValueArray(),
						cell.getValueOffset(), cell.getValueLength()));
				spanList.add(span);
				
			}
			
			TraceSpanTree tree = new TraceSpanTree();
			tree.build(spanList);
		} catch (Throwable e) {
			logger.error("Failed to mapper call chain[" + key.toString() + "]",
					e);
		}
	}
}
