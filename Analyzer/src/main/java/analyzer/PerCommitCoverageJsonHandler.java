package analyzer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.CommitRecord;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.function.Consumer;

public class PerCommitCoverageJsonHandler {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Append exactly ONE record to the target NDJSON file.
     * File is created if missing.
     */
    public static void appendRecord(Path out, PerCommitCoverage rec) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(
                out, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {

            w.write(MAPPER.writeValueAsString(rec)); // JSON
            w.newLine();                             // record delimiter
        }
    }

    /**
     * Read the file line‑by‑line and hand each CommitRecord to the caller.
     * No list is ever built, memory stays O(1).
     */
    public static void readRecordsStreaming(Path in,
                                            Consumer<PerCommitCoverage> consumer) throws IOException {
        try (BufferedReader br = Files.newBufferedReader(in, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                PerCommitCoverage rec = MAPPER.readValue(line, PerCommitCoverage.class);
                consumer.accept(rec);                // caller handles it
            }
        }
    }

    // Overwrite the file with an empty one
    public static void resetFile(Path file) {
        try {
            Files.write(file, new byte[0]);  // Truncate
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
