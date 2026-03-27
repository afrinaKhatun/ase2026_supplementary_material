package analyzer;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CoverageFileHandler {
    public static boolean commitFolderExists(String root, String targetFolderName) {
        Path rootFolder = Paths.get(root);
        Path targetFolder = rootFolder.resolve(targetFolderName);

        // Step 1: Check if folder exists
        if (!Files.isDirectory(targetFolder)) {
            //System.out.println("❌ Folder not found: " + targetFolder);
            return false;
        }
        //System.out.println("✅ Found folder: " + targetFolder);
        return true;
    }
}

