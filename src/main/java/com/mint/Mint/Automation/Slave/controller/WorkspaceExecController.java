package com.mint.Mint.Automation.Slave.controller;

import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/workspace")
@CrossOrigin(origins = "*")
public class WorkspaceExecController {

    @PostMapping("/exec/{teamId}/{sessionId}")
    public Map<String, Object> execInWorkspace(
            @PathVariable String teamId,
            @PathVariable String sessionId,
            @RequestBody Map<String, String> body
    ) {
        Map<String, Object> resp = new HashMap<>();
        String command = body.getOrDefault("command", "");

        if (command.trim().isEmpty()) {
            resp.put("success", false);
            resp.put("exitCode", -1);
            resp.put("output", "Error: command is empty.");
            return resp;
        }

        String containerName = "workspace_" + teamId + "_" + sessionId;

        try {
            // Run via: docker exec <container> /bin/bash -lc "<command>"
            ProcessBuilder pb = new ProcessBuilder(
                    "docker", "exec", containerName,
                    "/bin/bash", "-lc", command
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();

            String output = readAll(p.getInputStream());
            int exit = p.waitFor();

            resp.put("success", exit == 0);
            resp.put("exitCode", exit);
            resp.put("output", output);
            return resp;

        } catch (Exception e) {
            resp.put("success", false);
            resp.put("exitCode", -1);
            resp.put("output", "Error executing in container '" + containerName + "': " + e.getMessage());
            return resp;
        }
    }

    private String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int r;
        while ((r = in.read(buf)) != -1) {
            baos.write(buf, 0, r);
        }
        return baos.toString(StandardCharsets.UTF_8);
    }
}