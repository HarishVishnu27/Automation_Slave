package com.mint.Mint.Automation.Slave.controller;

import com.mint.Mint.Automation.Slave.service.WorkspaceManagerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/workspace")
@CrossOrigin(origins = "*")
public class DeviceController {

    @Autowired
    private WorkspaceManagerService workspaceService;

    // 1. Start the Docker Container
    @PostMapping("/init/{teamId}/{sessionId}")
    public String initWorkspace(@PathVariable String teamId, @PathVariable String sessionId, @RequestBody Map<String, String> envVars) {
        try{
            workspaceService.initializeWorkspace(teamId, sessionId, envVars);
            return "Workspace initialized";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    // 2. Sync Files from Node.js to the Spring Boot Hard Drive
    @PostMapping("/sync/{teamId}/{sessionId}")
    public String syncFiles(@PathVariable String teamId, @PathVariable String sessionId, @RequestBody List<Map<String, String>> items) {
        try {
            Path sessionDir = Paths.get("./workspaces", teamId, sessionId);
            Files.createDirectories(sessionDir);

            // Loop through the JSON array sent by Node.js
            for (Map<String, String> itemData : items) {
                String pathString = itemData.get("path");
                String type = itemData.get("type"); // 🟢 Read the new type flag

                Path targetPath = sessionDir.resolve(pathString);

                if ("folder".equals(type)) {
                    // 🟢 It's an empty folder, just create the directory
                    Files.createDirectories(targetPath);
                } else {
                    // 🟢 It's a file, create its parent directories and write the content
                    String content = itemData.getOrDefault("content", "");
                    Files.createDirectories(targetPath.getParent());
                    Files.write(targetPath, content.getBytes());
                }
            }
            return "Files synced successfully";
        } catch (Exception e) {
            return "Sync Error: " + e.getMessage();
        }
    }

    // DeviceController.java
    @DeleteMapping("/destroy/{teamId}/{sessionId}")
    public String destroyWorkspace(@PathVariable String teamId, @PathVariable String sessionId) {
        workspaceService.destroyWorkspace(teamId, sessionId);
        return "Workspace destroyed";
    }
}