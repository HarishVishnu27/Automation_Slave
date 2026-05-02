package com.mint.Mint.Automation.Slave.controller;

import com.mint.Mint.Automation.Slave.service.FileSystemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/fs")
@CrossOrigin(origins = "*")
public class FileSystemController {

    @Autowired
    private FileSystemService fileSystemService;

    @GetMapping("/{teamId}/tree")
    public Map<String, Object> getTree(@PathVariable String teamId) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Map<String, Object>> nodes = fileSystemService.getWorkspaceTree(teamId);
            response.put("success", true);
            response.put("nodes", nodes);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
        }
        return response;
    }

    @GetMapping("/{teamId}/file")
    public Map<String, Object> getFileContent(@PathVariable String teamId, @RequestParam String path) {
        Map<String, Object> response = new HashMap<>();
        try {
            response.put("success", true);
            response.put("content", fileSystemService.readFile(teamId, path));
        } catch (Exception e) {
            response.put("success", false);
        }
        return response;
    }

    @PostMapping("/{teamId}/file")
    public String saveFile(@PathVariable String teamId, @RequestParam String path, @RequestBody Map<String, String> payload) {
        try {
            fileSystemService.writeFile(teamId, path, payload.getOrDefault("content", ""));
            return "Saved";
        } catch (Exception e) { return "Error: " + e.getMessage(); }
    }

    @PostMapping("/{teamId}/folder")
    public String createFolder(@PathVariable String teamId, @RequestParam String path) {
        try {
            fileSystemService.createFolder(teamId, path);
            return "Created";
        } catch (Exception e) { return "Error: " + e.getMessage(); }
    }

    @DeleteMapping("/{teamId}/node")
    public String deleteNode(@PathVariable String teamId, @RequestParam String path) {
        try {
            fileSystemService.deleteNode(teamId, path);
            return "Deleted";
        } catch (Exception e) { return "Error: " + e.getMessage(); }
    }

    @PutMapping("/{teamId}/rename")
    public String renameNode(@PathVariable String teamId, @RequestParam String oldPath, @RequestParam String newPath) {
        try {
            fileSystemService.moveNode(teamId, oldPath, newPath);
            return "Renamed";
        } catch (Exception e) { return "Error: " + e.getMessage(); }
    }
}