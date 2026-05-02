package com.mint.Mint.Automation.Slave.service;

import org.springframework.stereotype.Service;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

@Service
public class FileSystemService {

    // 🟢 The New Permanent Vault
    private static final String BASE_STORAGE_DIR = "./storage/workspaces";

    // SECURITY: Prevents hackers from using "../" to escape their team folder
    private Path getSecurePath(String teamId, String relativePath) throws IOException {
        Path teamDir = Paths.get(BASE_STORAGE_DIR, teamId).toAbsolutePath().normalize();
        Files.createDirectories(teamDir);

        if (relativePath == null || relativePath.isEmpty() || relativePath.equals("null")) {
            return teamDir;
        }

        Path targetPath = teamDir.resolve(relativePath).normalize();
        if (!targetPath.startsWith(teamDir)) {
            throw new SecurityException("Illegal Path Traversal Detected");
        }
        return targetPath;
    }

    // 1. Generate the JSON Tree for React
    public List<Map<String, Object>> getWorkspaceTree(String teamId) throws IOException {
        Path teamDir = getSecurePath(teamId, null);
        List<Map<String, Object>> nodes = new ArrayList<>();

        // If directory is empty, seed it with App.java
        try (Stream<Path> stream = Files.list(teamDir)) {
            if (stream.findAny().isEmpty()) {
                Path defaultFile = teamDir.resolve("App.java");
                Files.writeString(defaultFile, "public class App {\n    public static void main(String[] args) {\n        System.out.println(\"Hello from Mint Automation!\");\n    }\n}");
            }
        }

        try (Stream<Path> stream = Files.walk(teamDir)) {
            stream.forEach(path -> {
                if (path.equals(teamDir)) return; // Skip the root folder itself

                Map<String, Object> node = new HashMap<>();
                // 🟢 The physical file path IS the new MongoDB _id!
                String id = teamDir.relativize(path).toString().replace("\\", "/");
                String parentId = path.getParent().equals(teamDir) ? null : teamDir.relativize(path.getParent()).toString().replace("\\", "/");

                node.put("_id", id);
                node.put("name", path.getFileName().toString());
                node.put("type", Files.isDirectory(path) ? "folder" : "file");
                node.put("parentId", parentId);

                nodes.add(node);
            });
        }
        return nodes;
    }

    // 2. Read File Content
    public String readFile(String teamId, String path) throws IOException {
        Path targetPath = getSecurePath(teamId, path);
        if (Files.exists(targetPath) && !Files.isDirectory(targetPath)) {
            return Files.readString(targetPath);
        }
        throw new NoSuchFileException("File not found");
    }

    // 3. Write/Update File Content
    public void writeFile(String teamId, String path, String content) throws IOException {
        Path targetPath = getSecurePath(teamId, path);
        Files.createDirectories(targetPath.getParent());
        Files.writeString(targetPath, content);
    }

    // 4. Create Folder
    public void createFolder(String teamId, String path) throws IOException {
        Path targetPath = getSecurePath(teamId, path);
        Files.createDirectories(targetPath);
    }

    // 5. Delete File/Folder
    public void deleteNode(String teamId, String path) throws IOException {
        Path targetPath = getSecurePath(teamId, path);
        deleteDirectoryRecursively(targetPath.toFile());
    }

    // 6. Rename/Move Node
    public void moveNode(String teamId, String oldPath, String newPath) throws IOException {
        Path sourcePath = getSecurePath(teamId, oldPath);
        Path destPath = getSecurePath(teamId, newPath);
        Files.createDirectories(destPath.getParent());
        Files.move(sourcePath, destPath, StandardCopyOption.REPLACE_EXISTING);
    }

    private void deleteDirectoryRecursively(File file) {
        if (file.isDirectory()) {
            File[] contents = file.listFiles();
            if (contents != null) {
                for (File f : contents) deleteDirectoryRecursively(f);
            }
        }
        file.delete();
    }
}