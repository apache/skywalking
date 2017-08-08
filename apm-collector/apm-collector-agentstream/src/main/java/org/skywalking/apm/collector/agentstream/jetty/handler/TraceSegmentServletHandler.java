package org.skywalking.apm.collector.agentstream.jetty.handler;

import com.google.gson.JsonElement;
import com.google.gson.stream.JsonReader;
import java.io.BufferedReader;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import org.skywalking.apm.collector.agentstream.jetty.handler.reader.TraceSegment;
import org.skywalking.apm.collector.agentstream.jetty.handler.reader.TraceSegmentJsonReader;
import org.skywalking.apm.collector.agentstream.worker.segment.SegmentParse;
import org.skywalking.apm.collector.server.jetty.ArgumentsParseException;
import org.skywalking.apm.collector.server.jetty.JettyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class TraceSegmentServletHandler extends JettyHandler {

    private final Logger logger = LoggerFactory.getLogger(TraceSegmentServletHandler.class);

    @Override public String pathSpec() {
        return "/segments";
    }

    @Override protected JsonElement doGet(HttpServletRequest req) throws ArgumentsParseException {
        throw new UnsupportedOperationException();
    }

    @Override protected JsonElement doPost(HttpServletRequest req) throws ArgumentsParseException {
        logger.debug("receive stream segment");
        try {
            BufferedReader bufferedReader = req.getReader();
            read(bufferedReader);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    private TraceSegmentJsonReader jsonReader = new TraceSegmentJsonReader();

    private void read(BufferedReader bufferedReader) throws IOException {
        JsonReader reader = new JsonReader(bufferedReader);
        reader.beginArray();
        while (reader.hasNext()) {
            SegmentParse segmentParse = new SegmentParse();
            TraceSegment traceSegment = jsonReader.read(reader);
            segmentParse.parse(traceSegment.getGlobalTraceIds(), traceSegment.getTraceSegmentObject());
        }
        reader.endArray();
    }
}
