package org.apache.skywalking.oap.server.storage.plugin.doris;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.stream.Collectors;

import org.apache.skywalking.oap.server.storage.plugin.doris.client.DorisClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

public abstract class DorisStoragePluginITBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(DorisStoragePluginITBase.class);

    // Using MySQLContainer as Doris is MySQL protocol compatible.
    // Official Doris image: apache/doris:tag. Find a recent, stable, non-all-in-one image if possible for faster startup.
    // The "all-in-one" images bundle FE and BE, which is needed for a self-contained test.
    // Example tag: "2.0.3" (check Docker Hub for latest stable tags for amd64/arm64)
    // For simplicity, using a mysql container to test JDBC compatibility.
    // To test with actual Doris, you might need a GenericContainer and more complex setup.
    // Let's try with a Doris image directly using MySQLContainer's framework.
    // This configuration assumes the Doris image behaves like MySQL for initial connection and JDBC.
    // The default user/password for apache/doris images are often root/"" or admin/"".
    // The image `apache/doris:2.0.3` is an all-in-one image.
    protected static MySQLContainer<?> dorisContainer;

    protected static String jdbcUrl;
    protected static String username = "root"; // Default Doris user
    protected static String password = "";   // Default Doris password for some images, or check specific image
    protected static StorageModuleDorisConfig config;
    protected static DorisClient dorisClient;

    @BeforeAll
    public static void setUpDoris() throws Exception {
        // It's better to use a specific Doris image if Testcontainers supports it directly
        // or use GenericContainer. For now, we leverage MySQLContainer due to protocol compatibility.
        // This might need adjustment if the Doris image doesn't initialize like a standard MySQL server
        // for the purpose of Testcontainers' MySQLContainer health checks.
        // Using a Doris image directly.
        DockerImageName dorisImage = DockerImageName.parse("apache/doris:2.0.3")
            .asCompatibleSubstituteFor("mysql");


        dorisContainer = new MySQLContainer<>(dorisImage)
            .withDatabaseName("skywalking_it") // Will be created by the container
            .withUsername(username)
            .withPassword(password) // Some Doris images might have an empty password for root by default
            .withExposedPorts(9030) // Doris FE JDBC port
            // MySQLContainer maps 3306 internally, we need to ensure it maps 9030 for Doris
            // This is tricky as MySQLContainer is hardcoded for 3306.
            // A better approach is GenericContainer for non-MySQL images.
            // For now, let's assume the JDBC driver can connect to the mapped 3306 port if the image starts FE on it,
            // OR if the image itself can be configured to use 3306 for its MySQL protocol port.
            // The `apache/doris` images expose 9030 for MySQL protocol.
            // We will override the JDBC URL construction.

            // .withCommand("--query_port=3306") // This won't work, command is for mysqld
            .withLogConsumer(outputFrame -> LOGGER.debug("DorisContainer: {}", outputFrame.getUtf8String()))
            .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(5)));
            // .withStartupAttempts(3); // Might be needed for slower CI

        // Due to MySQLContainer hardcoding port 3306, we might need to use GenericContainer
        // for better control if the above doesn't work as expected with Doris's 9030 port.
        // For now, let's try starting it. If connection fails, this is the area to revisit.
        // It's possible the MySQLContainer will map 9030 on the host to 3306 in the container,
        // but the Doris FE inside listens on 9030.

        LOGGER.info("Starting Doris Testcontainer with image: {}", dorisImage);
        try {
            dorisContainer.start();
            LOGGER.info("Doris Testcontainer started.");
        } catch (Exception e) {
            LOGGER.error("Failed to start Doris Testcontainer. Ensure Docker is running and the image {} is available/compatible.", dorisImage, e);
            // Attempt to provide more specific advice if possible
            if (e.getMessage() != null && e.getMessage().contains("Cannot connect to MySQL server")) {
                LOGGER.error("This might be due to MySQLContainer expecting a MySQL server but finding Doris. " +
                             "Consider using GenericContainer for Doris or ensuring the Doris image used starts FE on port 3306 " +
                             "or is fully MySQL-compatible for Testcontainers' health checks.");
            }
            throw e;
        }


        // Construct JDBC URL using the actual mapped port for 9030 if possible, or the default MySQL port if that's what TC exposes.
        // MySQLContainer.getJdbcUrl() will use the mapped 3306 port. We need to connect to Doris's 9030.
        // This is a common issue. Let's assume dorisContainer.getMappedPort(9030) would be correct if using GenericContainer.
        // With MySQLContainer, it assumes the service inside is on 3306.
        // If apache/doris:2.0.3 image's FE listens on 9030 for MySQL protocol, we need to get that mapped port.
        // However, MySQLContainer is specifically for MySQL and will try to connect to container's 3306.
        // This setup is likely to fail if Doris FE in the container is not on 3306.
        // For a quick test, we can try using the standard JDBC URL from MySQLContainer,
        // implying we hope Doris inside the container is accessible via the port it thinks is 3306.
        // This is often NOT the case. Doris FE listens on 9030.

        // jdbcUrl = dorisContainer.getJdbcUrl(); // This will use mapped 3306 port.
        // Correct approach for Doris (FE port 9030):
        jdbcUrl = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true",
                                dorisContainer.getHost(),
                                dorisContainer.getMappedPort(9030), // Get the mapped port for FE's 9030
                                dorisContainer.getDatabaseName());
        LOGGER.info("Doris Testcontainer JDBC URL: {}", jdbcUrl);


        config = new StorageModuleDorisConfig();
        config.setHost(dorisContainer.getHost());
        // config.setPort(dorisContainer.getFirstMappedPort()); // This would be the 3306 mapped port.
        config.setPort(dorisContainer.getMappedPort(9030)); // Use the 9030 mapped port.
        config.setUser(username);
        config.setPassword(password);
        config.setDatabase(dorisContainer.getDatabaseName());

        dorisClient = new DorisClient(config);
        // Connection test will be implicitly done by schema initialization
        // dorisClient.connect(); // Not needed here, client connects on demand or provider does.

        LOGGER.info("Initializing schema...");
        initializeSchema(dorisContainer.getJdbcUrl(), username, password); // Use the direct JDBC URL for schema tool
        LOGGER.info("Schema initialized.");
    }

    private static void initializeSchema(String jdbcUrlToUse, String user, String pass) throws SQLException, IOException {
        try (Connection conn = DriverManager.getConnection(jdbcUrlToUse, user, pass);
             Statement stmt = conn.createStatement()) {

            InputStream schemaStream = DorisStoragePluginITBase.class.getClassLoader()
                .getResourceAsStream("doris_schema.sql");
            if (schemaStream == null) {
                throw new IOException("Cannot find doris_schema.sql in classpath resources.");
            }
            String schemaSql = new BufferedReader(new InputStreamReader(schemaStream))
                .lines().collect(Collectors.joining("\n"));

            // Split SQL script into individual statements
            // Basic split by semicolon; may need refinement for complex SQL with semicolons in strings/comments
            String[] sqlStatements = schemaSql.split("(?m);\\s*$"); // Split by semicolon at end of line

            for (String sql : sqlStatements) {
                if (sql.trim().isEmpty() || sql.trim().startsWith("--")) {
                    continue;
                }
                LOGGER.debug("Executing DDL: {}", sql.trim());
                stmt.execute(sql.trim());
            }
            LOGGER.info("Schema script executed successfully.");
        } catch (SQLException e) {
            LOGGER.error("Failed to execute schema initialization script. JDBC URL: {}, User: {}", jdbcUrlToUse, user, e);
            throw e;
        } catch (IOException e) {
             LOGGER.error("Failed to read schema initialization script.", e);
            throw e;
        }
    }

    @AfterAll
    public static void tearDownDoris() {
        if (dorisClient != null) {
            // dorisClient.disconnect(); // DorisClient doesn't have a disconnect exposed to provider directly
        }
        if (dorisContainer != null) {
            LOGGER.info("Stopping Doris Testcontainer...");
            dorisContainer.stop();
            LOGGER.info("Doris Testcontainer stopped.");
        }
    }

    protected DorisClient getDorisClient() {
        return dorisClient;
    }

    protected StorageModuleDorisConfig getDorisConfig() {
        return config;
    }
}
