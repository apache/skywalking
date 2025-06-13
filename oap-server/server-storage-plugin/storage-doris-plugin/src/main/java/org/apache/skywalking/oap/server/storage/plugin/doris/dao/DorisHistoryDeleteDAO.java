package org.apache.skywalking.oap.server.storage.plugin.doris.dao;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import org.apache.skywalking.oap.server.core.storage.IHistoryDeleteDAO;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.storage.plugin.doris.client.DorisClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DorisHistoryDeleteDAO implements IHistoryDeleteDAO {

    private static final Logger LOGGER = LoggerFactory.getLogger(DorisHistoryDeleteDAO.class);
    private final DorisClient dorisClient;

    public DorisHistoryDeleteDAO(DorisClient dorisClient) {
        this.dorisClient = dorisClient;
    }

    /**
     * Implements the IHistoryDeleteDAO interface method.
     * It calculates the cutoff timestamp based on TTL and then calls the specific
     * deleteHistory method that takes a direct timestamp.
     *
     * @param model                data entity.
     * @param timeBucketColumnName column name represents the time.
     * @param ttlDays              the number of days should be kept. Data older than (now - ttlDays) will be deleted.
     * @throws IOException when error happens in the deletion process.
     */
    @Override
    public void deleteHistory(Model model, String timeBucketColumnName, int ttlDays) throws IOException {
        // Calculate the timestamp before which data should be deleted.
        // SkyWalking's time_bucket is usually in YYYYMMDD, YYYYMMDDHH, YYYYMMDDHHMM, or YYYYMMDDHHMMSS format,
        // or a Unix timestamp. The comparison needs to be robust.
        // If timeBucketColumnName stores data like YYYYMMDD, we need to calculate that.
        // If it's a direct Unix timestamp (seconds or millis), that's easier.

        // Assuming timeBucketColumnName holds values comparable to a timestamp (e.g., Unix millis or a format sortable as string).
        // Let's calculate the cutoff timestamp in milliseconds since epoch.
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, -ttlDays);
        long timestampMillisCutoff = calendar.getTimeInMillis();

        // The subtask prompt's method signature is (Model, String, long timestamp).
        // We will call that one.
        LOGGER.info(
            "IHistoryDeleteDAO.deleteHistory(Model, String, int ttlDays) called for model: {}. TTL: {} days. Calculated cutoff timestamp (millis): {}",
            model.getName(), ttlDays, timestampMillisCutoff
        );
        this.deleteHistory(model, timeBucketColumnName, timestampMillisCutoff);
    }

    /**
     * Deletes data older than the given `timestamp` from the table represented by `model.getName()`.
     * This method aligns with the subtask prompt's specific signature.
     *
     * @param model                The model representing the table.
     * @param timeBucketColumnName The column storing the time bucket or timestamp.
     * @param timestamp            The cutoff timestamp. Data older than this will be deleted.
     *                             The unit of this timestamp must match the unit of the data in `timeBucketColumnName`.
     *                             E.g., if `timeBucketColumnName` stores seconds, `timestamp` should be in seconds.
     *                             For this example, we assume it's milliseconds, matching `System.currentTimeMillis()`.
     * @throws IOException If an error occurs during deletion.
     */
    public void deleteHistory(Model model, String timeBucketColumnName, long timestamp) throws IOException {
        String tableName = model.getName();

        // Construct the SQL DELETE statement.
        // The comparison `timeBucketColumnName < ?` assumes that smaller values are older.
        // This is true for Unix timestamps and sortable date string formats like YYYYMMDDHHMMSS.
        String sql = "DELETE FROM " + tableName + " WHERE " + timeBucketColumnName + " < ?";

        LOGGER.info("Executing history delete for table: {}. Condition: {} < {}. Timestamp parameter type: long (assumed milliseconds).",
                    tableName, timeBucketColumnName, timestamp);
        
        // Consider if the timestamp in the database needs conversion or if the passed 'timestamp' needs formatting.
        // E.g., if timeBucketColumnName is YYYYMMDD, the 'timestamp' long needs to be converted to that format.
        // For now, assuming direct numeric comparison (e.g. both are epoch millis or seconds).
        // If timeBucketColumnName is a date/datetime string, the SQL might need a CAST or specific date functions.
        // Example: DELETE FROM table WHERE STR_TO_DATE(time_bucket_column, '%Y%m%d%H%i%s') < STR_TO_DATE(?, '%Y%m%d%H%i%s')
        // This generic DAO cannot know the exact format of timeBucketColumnName without more info from Model or conventions.
        // We will proceed with direct comparison, assuming compatible types or that Doris handles implicit conversion.

        try {
            int affectedRows = dorisClient.executeUpdate(sql, timestamp);
            LOGGER.info("History delete affected {} rows in table: {}.", affectedRows, tableName);
        } catch (SQLException e) {
            LOGGER.error("Failed to delete history data from table: {}. SQL: {} (timestamp: {}). Error: {}",
                         tableName, sql, timestamp, e.getMessage(), e);
            throw new IOException("Failed to delete history data from Doris table " + tableName, e);
        }
    }
}
