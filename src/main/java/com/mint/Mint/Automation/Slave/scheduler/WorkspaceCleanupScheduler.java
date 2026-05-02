package com.mint.Mint.Automation.Slave.scheduler;

import com.mint.Mint.Automation.Slave.service.WorkspaceManagerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class WorkspaceCleanupScheduler {

    @Autowired
    private WorkspaceManagerService workspaceService;

    private static final String BASE_WORKSPACES_DIR = "./workspaces";
    // Define max age (e.g., 12 hours) before a workspace is considered a "Zombie"
    private static final long MAX_AGE_HOURS = 12;

    // Runs automatically every hour (3600000 ms)
    @Scheduled(fixedRate = 3600000)
    public void cleanupZombieWorkspaces() {
        System.out.println("[CRON] Initiating Zombie Workspace Sweep...");
        File baseDir = new File(BASE_WORKSPACES_DIR);

        if (!baseDir.exists() || !baseDir.isDirectory()) {
            return;
        }

        Instant thresholdTime = Instant.now().minus(MAX_AGE_HOURS, ChronoUnit.HOURS);

        // Loop through Team folders
        File[] teamDirs = baseDir.listFiles(File::isDirectory);
        if (teamDirs != null) {
            for (File teamDir : teamDirs) {
                String teamId = teamDir.getName();

                // Loop through Session folders inside the Team folder
                File[] sessionDirs = teamDir.listFiles(File::isDirectory);
                if (sessionDirs != null) {
                    for (File sessionDir : sessionDirs) {
                        Instant lastModified = Instant.ofEpochMilli(sessionDir.lastModified());

                        // If folder hasn't been touched in 12 hours, NUKE it.
                        if (lastModified.isBefore(thresholdTime)) {
                            String sessionId = sessionDir.getName();
                            System.out.println("[CRON] Found Zombie Session: " + sessionId + ". Terminating...");
                            // This uses your existing logic to stop Docker and delete the folder
                            workspaceService.destroyWorkspace(teamId, sessionId);
                        }
                    }
                }
            }
        }
        System.out.println("[CRON] Zombie Sweep Complete.");
    }
}