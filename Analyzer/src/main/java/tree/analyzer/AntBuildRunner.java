package tree.analyzer;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

public class AntBuildRunner {

    /** Run ant with optional targets. If no targets are provided, runs default target ("ant"). */
    public static int compileProjectWithAnt(String repoPath, String projectName) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        cmd.add("ant");
        if(projectName.equals("commons-io")){
            cmd.add("-q");
            cmd.add("compile.tests");
        }

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(new File(repoPath));
        pb.redirectErrorStream(true);

        Process p = pb.start();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) {
                System.out.println("[ant]: " + line);
            }
        }

        int exit = p.waitFor();
        System.out.println("[ant] Exit code: " + exit + " for command: " + String.join(" ", cmd));
        return exit;
    }

    /**
     * Heuristic search for test class output directory after Ant runs.
     * Fast-path checks common locations, then falls back to scanning for any directory with .class files.
     */
    private static Path findAntTestClassesRoot(Path repoRoot) throws IOException {
        // Common test output dirs (commons-collections 3.x often uses build/tests)
        List<Path> candidates = List.of(
                repoRoot.resolve("build/tests"),
                repoRoot.resolve("build/test-classes"),
                repoRoot.resolve("build/test/classes"),
                repoRoot.resolve("build/test"),
                repoRoot.resolve("target/test-classes"), // hybrid builds
                repoRoot.resolve("build/classes"),
                repoRoot.resolve("build/test-classes/java"),
                repoRoot.resolve("build/tests/java"),
                repoRoot.resolve("build/junit"),
                repoRoot.resolve("build/junit/classes"),
                repoRoot.resolve("target/classes")// sometimes tests end up here

        );

        for (Path c : candidates) {
            if (Files.isDirectory(c) && containsClassFiles(c, 10)) {
                return c;
            }
        }

        // Fallback: scan for any directory containing .class files (avoid .git and src trees)
        try (Stream<Path> s = Files.walk(repoRoot, 10)) {
            return s.filter(Files::isDirectory)
                    .filter(d -> {
                        String rel = repoRoot.relativize(d).toString().replace('\\', '/');
                        return !rel.startsWith(".git") && !rel.startsWith("src");
                    })
                    .filter(d -> {
                        try { return containsClassFiles(d, 10); }
                        catch (IOException e) { return false; }
                    })
                    // Prefer shallower (more "root-like") dirs
                    .min(Comparator.comparingInt(d -> repoRoot.relativize(d).getNameCount()))
                    .orElse(null);
        }
    }

    private static boolean containsClassFiles(Path dir, int depth) throws IOException {
        try (Stream<Path> s = Files.walk(dir, depth)) {
            return s.anyMatch(p -> p.toString().endsWith(".class"));
        }
    }
}
