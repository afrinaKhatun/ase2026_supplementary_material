package org.example;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.function.Consumer;

public class CommitRecordJsonHandler {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Append exactly ONE record to the target NDJSON file.
     * File is created if missing.
     */
    public static void appendRecord(Path out, CommitRecord rec) throws IOException {
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
                                     Consumer<CommitRecord> consumer) throws IOException {
        try (BufferedReader br = Files.newBufferedReader(in, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                CommitRecord rec = MAPPER.readValue(line, CommitRecord.class);
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
