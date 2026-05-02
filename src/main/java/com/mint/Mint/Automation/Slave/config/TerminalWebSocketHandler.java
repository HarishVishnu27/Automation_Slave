package com.mint.Mint.Automation.Slave.config;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TerminalWebSocketHandler extends TextWebSocketHandler {

    private final Map<String, OutputStream> sessionOutputStreams = new ConcurrentHashMap<>();
    private final Map<String, Process> activeProcesses = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {

        String query = session.getUri().getQuery();

        // Format: teamId=Fastest&sessionId=socket123

        String teamId = query.split("teamId=")[1].split("&")[0];
        String userSessionId = query.split("sessionId=")[1].split("&")[0];

        String containerName = "workspace_" + teamId + "_" + userSessionId;

//        ProcessBuilder pb = new ProcessBuilder("docker", "exec", "-i", containerName, "/bin/bash", "-i");

        ProcessBuilder pb = new ProcessBuilder("docker", "exec", "-i", "-e", "TERM=xterm", containerName, "/bin/bash", "-i");
        pb.redirectErrorStream(true);
        Process bashProcess = pb.start();

        activeProcesses.put(session.getId(), bashProcess);
        sessionOutputStreams.put(session.getId(), bashProcess.getOutputStream());

        // Send a welcome message so you know the connection is live
        if (session.isOpen()) {
            session.sendMessage(new TextMessage("\r\n\u001B[32m[Mint System] Connected to execution engine.\u001B[0m\r\n$ "));
        }

        new Thread(() -> {
            try {
                InputStream inputStream = bashProcess.getInputStream();
                byte[] buffer = new byte[1024];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    String output = new String(buffer, 0, read);

                    output = output.replaceAll("bash: cannot set terminal process group.*?\\r?\\n", "");
                    output = output.replaceAll("bash: no job control in this shell.*?\\r?\\n", "");

                    if (session.isOpen() && !output.isEmpty()) {
                        session.sendMessage(new TextMessage(output));
                    }
                }
            } catch (Exception e) {
                System.err.println("Terminal stream closed for session: " + session.getId());
            }
        }).start();

        System.out.println("Terminal connected for session: " + session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        OutputStream bashInput = sessionOutputStreams.get(session.getId());
        if (bashInput != null) {
            try {
                String command = message.getPayload();

                // CRITICAL FIX: Bash needs the "Enter" key to execute.
                if (!command.endsWith("\n")) {
                    command += "\n";
                }

                bashInput.write(command.getBytes());
                bashInput.flush();
            } catch (Exception e) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage("\r\n\u001B[31m[System Error] The terminal process died or the container does not exist.\u001B[0m\r\n"));
                }
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Process process = activeProcesses.remove(session.getId());
        if (process != null) {
            process.destroyForcibly();
        }
        sessionOutputStreams.remove(session.getId());
        System.out.println("Terminal disconnected: " + session.getId());
    }
}
//package com.mint.Mint.Automation.Slave.config;
//
//import org.springframework.stereotype.Component;
//import org.springframework.web.socket.CloseStatus;
//import org.springframework.web.socket.TextMessage;
//import org.springframework.web.socket.WebSocketSession;
//import org.springframework.web.socket.handler.TextWebSocketHandler;
//
//import java.io.InputStream;
//import java.io.OutputStream;
//import java.util.Map;
//import java.util.concurrent.ConcurrentHashMap;
//
//@Component
//public class TerminalWebSocketHandler extends TextWebSocketHandler {
//
//    private final Map<String, OutputStream> sessionOutputStreams = new ConcurrentHashMap<>();
//    private final Map<String, Process> activeProcesses = new ConcurrentHashMap<>();
//
//    @Override
//    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
//        String query = session.getUri().getQuery();
//        String teamId = query.split("teamId=")[1];
//        String containerName = "workspace_" + teamId;
//
//        // Open an interactive bash shell inside the team's running Docker container
//        ProcessBuilder pb = new ProcessBuilder("docker", "exec", "-i", containerName, "/bin/bash");
//        pb.redirectErrorStream(true);
//        Process bashProcess = pb.start();
//
//        activeProcesses.put(session.getId(), bashProcess);
//        sessionOutputStreams.put(session.getId(), bashProcess.getOutputStream());
//
//        // FIX: Use raw InputStream bytes instead of BufferedReader to prevent line-locking
//        new Thread(() -> {
//            try {
//                InputStream inputStream = bashProcess.getInputStream();
//                byte[] buffer = new byte[1024];
//                int read;
//                while ((read = inputStream.read(buffer)) != -1) {
//                    String output = new String(buffer, 0, read);
//                    if (session.isOpen()) {
//                        session.sendMessage(new TextMessage(output));
//                    }
//                }
//            } catch (Exception e) {
//                System.err.println("Terminal stream closed for session: " + session.getId());
//            }
//        }).start();
//
//        System.out.println("Terminal connected for session: " + session.getId());
//    }
//
//    @Override
//    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
//        OutputStream bashInput = sessionOutputStreams.get(session.getId());
//        if (bashInput != null) {
//            try {
//                bashInput.write(message.getPayload().getBytes());
//                bashInput.flush();
//            } catch (Exception e) {
//                // If the pipe is closed/dead, notify the frontend cleanly
//                if (session.isOpen()) {
//                    session.sendMessage(new TextMessage("\r\n\u001B[31m[System Error] The terminal process died or the container does not exist. Please re-initialize.\u001B[0m\r\n"));
//                }
//            }
//        }
//    }
//
//    @Override
//    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
//        Process process = activeProcesses.remove(session.getId());
//        if (process != null) {
//            process.destroyForcibly();
//        }
//        sessionOutputStreams.remove(session.getId());
//        System.out.println("Terminal disconnected: " + session.getId());
//    }
//}