package com.mint.Mint.Automation.Slave.service;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DeviceAutomationService {

    // Path to your downloaded Selenium/Appium JARs.
    // Ensure this folder exists at the root of your project!
//    private static final String SHARED_LIBS_DIR = "./shared-libs/*";
    private static final String SHARED_LIBS_DIR = "./shared-libs";

    public Map<String, Object> executeCode(String code, String extension, long timeoutSeconds, String teamName, String userName, Map<String, String> envVars) {
        Map<String, Object> response = new HashMap<>();
        String executionId = UUID.randomUUID().toString();
        Path executionDir = null;

        try {
            String safeTeam = teamName.replaceAll("[^a-zA-Z0-9-_]", "_");
            String safeUser = userName.replaceAll("[^a-zA-Z0-9-_]", "_");

            executionDir = Paths.get("./code-execution/" + safeTeam + "/" + safeUser + "/" + executionId);
            Files.createDirectories(executionDir);

            String fileName = getFileName(code, extension);
            Path codeFile = executionDir.resolve(fileName + "." + extension);
            Files.write(codeFile, code.getBytes());

            String output;
            if (extension.equals("java")) {
                output = compileAndRunJava(executionDir, fileName, timeoutSeconds, envVars);
            } else if (extension.equals("py")) {
                output = runPython(executionDir, fileName, timeoutSeconds, envVars);
            } else {
                throw new Exception("Unsupported extension: " + extension);
            }

            response.put("success", true);
            response.put("output", output);

        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        } finally {
            if (executionDir != null) {
                deleteDirectory(executionDir.toFile());
            }
        }
        return response;
    }

    public String getFileName(String code, String extension) {
        if (extension.equals("java")) {
            Pattern pattern = Pattern.compile("public\\s+class\\s+(\\w+)");
            Matcher matcher = pattern.matcher(code);
            if (matcher.find()) {
                return matcher.group(1);
            }
            return "Main";
        }
        return "script";
    }

    private String compileAndRunJava(Path directory, String className, long timeoutSeconds, Map<String, String> envVars) throws Exception {
        // 1. Build OS-agnostic Classpath (Current Dir + Shared Libs)
//        String absoluteLibsPath = Paths.get(SHARED_LIBS_DIR).toAbsolutePath().toString();
//        String classPath = "." + File.pathSeparator + absoluteLibsPath;
        String absoluteLibsPath = Paths.get(SHARED_LIBS_DIR).toAbsolutePath().toString();
// We append the separator and wildcard here!
        String classPath = "." + File.pathSeparator + absoluteLibsPath + File.separator + "*";

        // --- COMPILATION (Direct javac) ---
        ProcessBuilder compilePb = new ProcessBuilder("javac", "-cp", classPath, className + ".java")
                .directory(directory.toFile())
                .redirectErrorStream(true);

        Process compileProcess = compilePb.start();
        String compileOutput = readOutput(compileProcess);

        if (!compileProcess.waitFor(30, TimeUnit.SECONDS)) { // javac should be very fast
            compileProcess.destroyForcibly();
            throw new Exception("Compilation timeout");
        }
        if (compileProcess.exitValue() != 0) {
            throw new Exception("Compilation error:\n" + compileOutput);
        }

        // --- EXECUTION (Direct java) ---
        Path outputFile = directory.resolve("execution_output.log");

        ProcessBuilder runPb = new ProcessBuilder("java", "-cp", classPath, className)
                .directory(directory.toFile())
                .redirectErrorStream(true) // Merge stderr into stdout
                .redirectOutput(outputFile.toFile()); // FIX: Stream directly to file to prevent buffer lockups

        // Inject Environment Variables
        if (envVars != null && !envVars.isEmpty()) {
            runPb.environment().putAll(envVars);
        }

        Process runProcess = runPb.start();

        if (!runProcess.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            runProcess.destroyForcibly();
            throw new Exception("Execution timeout after " + timeoutSeconds + " seconds");
        }

        // Read the result from the file safely after the process has finished
        String executionResult = Files.exists(outputFile) ? Files.readString(outputFile) : "";

        if (runProcess.exitValue() != 0) {
            throw new Exception("Runtime execution error:\n" + executionResult.trim());
        }

        return executionResult.trim();
    }

    private String runPython(Path directory, String fileName, long timeoutSeconds, Map<String, String> envVars) throws Exception {
        Path outputFile = directory.resolve("execution_output.log");

        ProcessBuilder pb = new ProcessBuilder("python", fileName + ".py")
                .directory(directory.toFile())
                .redirectErrorStream(true)
                .redirectOutput(outputFile.toFile()); // FIX: Stream directly to file

        if (envVars != null) pb.environment().putAll(envVars);

        Process process = pb.start();

        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new Exception("Python execution timeout after " + timeoutSeconds + " seconds");
        }

        String executionResult = Files.exists(outputFile) ? Files.readString(outputFile) : "";

        if (process.exitValue() != 0) {
            throw new Exception("Runtime error:\n" + executionResult.trim());
        }
        return executionResult.trim();
    }

    private String readOutput(Process process) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }
        return output.toString().trim();
    }

    private void deleteDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        directory.delete();
    }
}


//package com.mint.Mint.Automation.Slave.service;
//
//import com.mint.Mint.Automation.Slave.repository.DeviceRepository;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Component;
//
//import java.io.BufferedReader;
//import java.io.File;
//import java.io.IOException;
//import java.io.InputStreamReader;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import java.util.HashMap;
//import java.util.Map;
//import java.util.UUID;
//import java.util.concurrent.TimeUnit;
//import java.util.regex.Matcher;
//import java.util.regex.Pattern;
//
//@Component
//public class DeviceAutomationService {
//
//    @Autowired
//    DeviceRepository deviceRepository;
//
//    private static final long DEFAULT_TIMEOUT_SECONDS = 300;
//
////    public Map<String, Object> executeCode(String code, String extension, long timeoutSeconds)
////    {
////        Map<String, Object> response = new HashMap<>();
////        String executionId = UUID.randomUUID().toString();
////        Path executionDir = null;
////
////        try{
////            executionDir = Paths.get("./code-execution/" + executionId);
////            Files.createDirectories(executionDir);
////
////            String fileName = getFileName(code, extension);
////            Path codeFile = executionDir.resolve(fileName + "." + extension);
////            Files.write(codeFile, code.getBytes());
////
////            String output;
////            System.out.println("executionDir - " + executionDir);
////            System.out.println("fileName - " + fileName);
////            if(extension.equals("java"))
////            {
////                createPomXml(executionDir, fileName);
////                output = compileAndRunJava(executionDir, fileName, timeoutSeconds);
////            } else if (extension.equals("py")) {
////                output = runPython(executionDir, fileName, timeoutSeconds);
////            }else{
////                throw new Exception("Unsupported extension: " + extension);
////            }
////
////            response.put("success", true);
////            response.put("output", output);
////
////        }catch (Exception e){
//    ////            System.out.println("Error!" + e);
////            response.put("success", false);
////            response.put("error", e.getMessage());
////            e.printStackTrace();
////        } finally {
////            if(executionDir != null)
////            {
////                deleteDirectory(executionDir.toFile());
////            }
////        }
////
////        return response;
////    }
//    public Map<String, Object> executeCode(String code, String extension, long timeoutSeconds, String teamName, String userName, Map<String, String> envVars) {
//        Map<String, Object> response = new HashMap<>();
//        String executionId = UUID.randomUUID().toString();
//        Path executionDir = null;
//
//        try {
//            // 1. Sanitize folder names to prevent path traversal
//            String safeTeam = teamName.replaceAll("[^a-zA-Z0-9-_]", "_");
//            String safeUser = userName.replaceAll("[^a-zA-Z0-9-_]", "_");
//
//            // 2. Create the specific directory structure
//            executionDir = Paths.get("./code-execution/" + safeTeam + "/" + safeUser + "/" + executionId);
//            Files.createDirectories(executionDir);
//
//            String fileName = getFileName(code, extension);
//            Path codeFile = executionDir.resolve(fileName + "." + extension);
//            Files.write(codeFile, code.getBytes());
//
//            String output;
//            if (extension.equals("java")) {
//                createPomXml(executionDir, fileName);
//                // Pass envVars to the compile/run step
//                output = compileAndRunJava(executionDir, fileName, timeoutSeconds, envVars);
//            } else if (extension.equals("py")) {
//                output = runPython(executionDir, fileName, timeoutSeconds, envVars);
//            } else {
//                throw new Exception("Unsupported extension: " + extension);
//            }
//
//            response.put("success", true);
//            response.put("output", output);
//
//        } catch (Exception e) {
//            response.put("success", false);
//            response.put("error", e.getMessage());
//            e.printStackTrace();
//        } finally {
//            if (executionDir != null) {
//                deleteDirectory(executionDir.toFile());
//            }
//        }
//        return response;
//    }
//
//    public String getFileName(String code, String extension){
//        if(extension.equals("java")){
//            Pattern pattern = Pattern.compile("public\\s+class\\s+(\\w+)");
//            Matcher matcher = pattern.matcher(code);
//
//            if(matcher.find()){
//                System.out.println(matcher.group(1));
//                return matcher.group(1);
//            }
//
//            return "Main";
//        }
//        return "script";
//    }
//
//    private void createPomXml(Path directory, String className) throws IOException {
//        String pomContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
//                "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
//                "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
//                "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0\n" +
//                "         http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
//                "    <modelVersion>4.0.0</modelVersion>\n" +
//                "\n" +
//                "    <groupId>com.coderunner</groupId>\n" +
//                "    <artifactId>temp-execution</artifactId>\n" +
//                "    <version>1.0-SNAPSHOT</version>\n" +
//                "\n" +
//                "    <properties>\n" +
//                "        <maven.compiler.source>21</maven.compiler.source>\n" +
//                "        <maven.compiler.target>21</maven.compiler.target>\n" +
//                "        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>\n" +
//                "    </properties>\n" +
//                "\n" +
//                "    <dependencies>\n" +
//                "        <!-- Selenium Java -->\n" +
//                "        <dependency>\n" +
//                "            <groupId>org.seleniumhq.selenium</groupId>\n" +
//                "            <artifactId>selenium-java</artifactId>\n" +
//                "            <version>4.38.0</version>\n" +
//                "        </dependency>\n" +
//                "\n" +
//                "        <!-- Appium Java Client -->\n" +
//                "        <dependency>\n" +
//                "            <groupId>io.appium</groupId>\n" +
//                "            <artifactId>java-client</artifactId>\n" +
//                "            <version>10.0.0</version>\n" +
//                "        </dependency>\n" +
//                "\n" +
//                "        <!-- TestNG (optional, for test frameworks) -->\n" +
//                "        <dependency>\n" +
//                "            <groupId>org.testng</groupId>\n" +
//                "            <artifactId>testng</artifactId>\n" +
//                "            <version>7.8.0</version>\n" +
//                "        </dependency>\n" +
//                "\n" +
//                "        <!-- JUnit (optional) -->\n" +
//                "        <dependency>\n" +
//                "            <groupId>org.junit.jupiter</groupId>\n" +
//                "            <artifactId>junit-jupiter</artifactId>\n" +
//                "            <version>5.10.1</version>\n" +
//                "        </dependency>\n" +
//                "    </dependencies>\n" +
//                "\n" +
//                "    <build>\n" +
//                "        <plugins>\n" +
//                "            <plugin>\n" +
//                "                <groupId>org.codehaus.mojo</groupId>\n" +
//                "                <artifactId>exec-maven-plugin</artifactId>\n" +
//                "                <version>3.1.0</version>\n" +
//                "                <configuration>\n" +
//                "                    <mainClass>" + className + "</mainClass>\n" +
//                "                </configuration>\n" +
//                "            </plugin>\n" +
//                "        </plugins>\n" +
//                "    </build>\n" +
//                "</project>";
//
//        Path pomFile = directory.resolve("pom.xml");
//        Files.write(pomFile, pomContent.getBytes());
//    }
//
////    private String compileAndRunJava(Path directory, String className, long timeoutSeconds) throws Exception{
////        Path srcDir = directory.resolve("src/main/java");
////        Files.createDirectories(srcDir);
////
////        Path originalFile = directory.resolve(className + ".java");
////        Path targetFile = srcDir.resolve(className + ".java");
////        Files.move(originalFile, targetFile);
////
////        String mvnPath = "C:\\Program Files\\apache-maven-3.9.11\\bin\\mvn.cmd";
////
////        Process compileProcess = new ProcessBuilder(mvnPath, "clean", "compile").directory(directory.toFile())
////                .redirectErrorStream(true)
////                .start();
////
////        String compileOutput = readOutput(compileProcess);
//////        compileProcess.waitFor();
////
////        boolean compileFinished = compileProcess.waitFor(2, TimeUnit.MINUTES);
////        if(!compileFinished)
////        {
////            compileProcess.destroyForcibly();
////            throw new Exception("Maven compilation timeout (2 minutes exceeded)");
////        }
////
////        if(compileProcess.exitValue() != 0){
////            throw new Exception("Maven compilation error: " + compileOutput);
////        }
//////        System.out.println("Problem is here !!");
////        Process runProcess = new ProcessBuilder(mvnPath, "exec:java")
////                .directory(directory.toFile())
////                .redirectErrorStream(true)
////                .start();
////        String output = readOutput(runProcess);
//////        runProcess.waitFor();
////
////        boolean runFinished = runProcess.waitFor(timeoutSeconds, TimeUnit.SECONDS);
////
////        if(!runFinished)
////        {
////            runProcess.destroyForcibly();
////            throw new Exception("Execution timeout after " + timeoutSeconds + " seconds");
////        }
////
////        if(runProcess.exitValue() != 0)
////        {
////            throw new Exception("Maven execution error: " + output);
////        }
//    ////        System.out.println("output - " + output);
//    ////        return output;
////        return cleanMavenOutput(output);
////    }
//
//    private String compileAndRunJava(Path directory, String className, long timeoutSeconds, Map<String, String> envVars) throws Exception {
//        Path srcDir = directory.resolve("src/main/java");
//        Files.createDirectories(srcDir);
//
//        Path originalFile = directory.resolve(className + ".java");
//        Path targetFile = srcDir.resolve(className + ".java");
//        Files.move(originalFile, targetFile);
//
//        String mvnPath = "C:\\Program Files\\apache-maven-3.9.11\\bin\\mvn.cmd";
//
//        // --- COMPILATION ---
//        Process compileProcess = new ProcessBuilder(mvnPath, "clean", "compile")
//                .directory(directory.toFile())
//                .redirectErrorStream(true)
//                .start();
//
//        String compileOutput = readOutput(compileProcess);
//        if (!compileProcess.waitFor(2, TimeUnit.MINUTES)) {
//            compileProcess.destroyForcibly();
//            throw new Exception("Maven compilation timeout (2 minutes exceeded)");
//        }
//        if (compileProcess.exitValue() != 0) {
//            throw new Exception("Maven compilation error: " + compileOutput);
//        }
//
//        // --- EXECUTION (Injecting Environment Variables) ---
//        ProcessBuilder runPb = new ProcessBuilder(mvnPath, "exec:java")
//                .directory(directory.toFile())
//                .redirectErrorStream(true);
//
//        // 3. Inject variables directly into the OS process memory
//        if (envVars != null && !envVars.isEmpty()) {
//            Map<String, String> processEnv = runPb.environment();
//            processEnv.putAll(envVars);
//        }
//
//        Process runProcess = runPb.start();
//        String output = readOutput(runProcess);
//
//        if (!runProcess.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
//            runProcess.destroyForcibly();
//            throw new Exception("Execution timeout after " + timeoutSeconds + " seconds");
//        }
//        if (runProcess.exitValue() != 0) {
//            throw new Exception("Maven execution error: " + output);
//        }
//
//        return cleanMavenOutput(output);
//    }
//
//    private String cleanMavenOutput(String mavenOutput)
//    {
//        StringBuilder cleanOutput = new StringBuilder();
//        String[] lines = mavenOutput.split("\n");
//        boolean capturing = false;
//
//        for(String line: lines)
//        {
//            if(line.contains("--- exec-maven-plugin") || line.contains("exec:java")){
//                capturing = true;
//                continue;
//            }
//
//            if(line.contains("BUILD SUCCESS") || line.contains("BUILD FAILURE") || line.contains("Total time:"))
//            {
//                break;
//            }
//
//            if(capturing && !line.contains("[INFO]") && !line.startsWith("[WARNING]") && !line.startsWith("[ERROR]") && !line.trim().isEmpty() )
//            {
//                cleanOutput.append(line).append("\n");
//            }
//        }
//
//        String result = cleanOutput.toString().trim();
//        return result.isEmpty() ? mavenOutput : result;
//    }
//
////    private String runPython(Path directory, String fileName, long timeoutSeconds) throws Exception{
////        Process process = new ProcessBuilder("python", fileName + ".py")
////                .directory(directory.toFile())
////                .redirectErrorStream(true)
////                .start();
////        process.waitFor();
////
////        String output = readOutput(process);
////
////        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
////
////        if(!finished)
////        {
////            process.destroyForcibly();
////            throw new Exception("Python execution timeout after " + timeoutSeconds + " seconds");
////        }
////
////        if(process.exitValue() != 0)
////        {
////            throw new Exception("Runtime error: " + output);
////        }
////
////        return output;
////    }
//
//    private String runPython(Path directory, String fileName, long timeoutSeconds, Map<String, String> envVars) throws Exception {
//        ProcessBuilder pb = new ProcessBuilder("python", fileName + ".py")
//                .directory(directory.toFile())
//                .redirectErrorStream(true);
//
//        if (envVars != null) pb.environment().putAll(envVars);
//
//        Process process = pb.start();
//        String output = readOutput(process);
//
//        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
//            process.destroyForcibly();
//            throw new Exception("Python execution timeout");
//        }
//        if (process.exitValue() != 0) throw new Exception("Runtime error: " + output);
//        return output;
//    }
//
//    private String readOutput(Process process) throws IOException{
//        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
//
//        StringBuilder output = new StringBuilder();
//        String line;
//
//        while((line = reader.readLine()) != null)
//        {
//            output.append(line).append("\n");
//        }
//        return output.toString().trim();
//    }
//
//    private void deleteDirectory(File directory)
//    {
//        File[] files = directory.listFiles();
//        if(files != null)
//        {
//            for(File file : files){
//                if(file.isDirectory())
//                {
//                    deleteDirectory(file);
//                }else{
//                    file.delete();
//                }
//            }
//        }
//        directory.delete();
//    }
//
//}
