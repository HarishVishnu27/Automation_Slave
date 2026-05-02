package com.mint.Mint.Automation.Slave.service;

import org.springframework.stereotype.Service;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@Service
public class WorkspaceManagerService {

    private static final String BASE_WORKSPACES_DIR = "./workspaces";
    private static final String DOCKER_IMAGE = "mint-automation-base";

    // 1. Initialize the Container for the Session (Direct Vault Mount)
    public void initializeWorkspace(String teamId, String sessionId, Map<String, String> envVars) throws Exception {
        // 🟢 FIX 1: Point directly to the Permanent Storage Vault
        Path vaultDir = Paths.get("./storage/workspaces", teamId).toAbsolutePath().normalize();
        Files.createDirectories(vaultDir);

        String containerName = "workspace_" + teamId + "_" + sessionId;
        if (isContainerRunning(containerName)) {
            return;
        }

        java.util.List<String> command = new java.util.ArrayList<>();
        command.add("docker");
        command.add("run");
        command.add("-d");
        command.add("--rm");
        command.add("--name");
        command.add(containerName);

        command.add("--network");
        command.add("host");

        // 🟢 FIX 2: Resource Limits (The Noisy Neighbor Fix)
        // Restricts container to 1 CPU core and 1GB RAM max.
        command.add("--cpus=1.0");
        command.add("--memory=1g");

        // 🟢 FIX 3: Mount the Persistent Vault!
        // Now, Terminal `mkdir` directly modifies the permanent files, and multiple
        // users on the same team share the exact same physical folder.
        String absolutePath = vaultDir.toString().replace("\\", "/");
        command.add("-v");
        command.add(absolutePath + ":/app");

        // Map the shared-libs directory into Docker as Read-Only (:ro)
        Path sharedLibsDir = Paths.get("./shared-libs");
        if (Files.exists(sharedLibsDir)) {
            String sharedLibsAbsolute = sharedLibsDir.toAbsolutePath().toString().replace("\\", "/");
            command.add("-v");
            command.add(sharedLibsAbsolute + ":/app/shared-libs:ro");
        }

        command.add("-w");
        command.add("/app");

        if (envVars != null) {
            envVars.forEach((key, value) -> {
                command.add("-e");
                command.add(key + "=" + value);
            });
        }

        command.add(DOCKER_IMAGE);
        command.add("tail");
        command.add("-f");
        command.add("/dev/null");

        ProcessBuilder pb = new ProcessBuilder(command);
        Process process = pb.start();
        process.waitFor();

        if (process.exitValue() != 0) {
            throw new Exception("Failed to start Docker container for session: " + sessionId);
        }
    }

    // 2. Destroy the Container when the session ends
    // FIX 2: Updated signature and logic to target the specific session, not the whole team
    public void destroyWorkspace(String teamId, String sessionId) {
        String containerName = "workspace_" + teamId + "_" + sessionId;
        try {
            // Stop the session's container (the --rm flag in 'docker run' will auto-remove it)
            new ProcessBuilder("docker", "stop", containerName).start().waitFor();

            // Delete physical files for the SESSION only, preserving other users on the team
            File sessionDir = Paths.get(BASE_WORKSPACES_DIR, teamId, sessionId).toFile();
            deleteDirectory(sessionDir);

            System.out.println("Destroyed Workspace for Session: " + sessionId);
        } catch (Exception e) {
            System.err.println("Error destroying workspace: " + e.getMessage());
        }
    }

    private boolean isContainerRunning(String containerName) {
        try {
            Process process = new ProcessBuilder("docker", "ps", "-q", "-f", "name=" + containerName).start();
            process.waitFor();
            return process.getInputStream().read() != -1; // If it returns bytes, it's running
        } catch (Exception e) {
            return false;
        }
    }

    private void deleteDirectory(File directory) {
        if (directory == null || !directory.exists()) return;
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) deleteDirectory(file);
                else file.delete();
            }
        }
        directory.delete();
    }
}