package com.epam.healenium.healenium_proxy.service;

import com.epam.healenium.healenium_proxy.model.SessionLogResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

@Slf4j(topic = "healenium")
@RequiredArgsConstructor
@Service
public class LogService {

    private static final String SESSION_START_PATTERN = "Init Session: sessionId=";
    private static final DateTimeFormatter LOG_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    @Value("${logging.path:./logs}")
    private String proxyLogsDir;

    /**
     * Extract logs for a specific session ID from all log files
     * @param sessionId The session ID to get logs for
     * @return SessionLogResult containing the session logs and timestamps
     */
    public SessionLogResultDto getLogsForSession(String sessionId) {
        List<Path> logFiles = getAllLogFiles();

        if (logFiles.isEmpty()) {
            return new SessionLogResultDto("No log files found", null, null);
        }

        Collections.sort(logFiles);

        StringBuilder sessionLogs = new StringBuilder();
        boolean foundSession = false;
        LocalDateTime startTime = null;
        LocalDateTime endTime = null;

        for (Path logFile : logFiles) {
            try {
                SessionLogResultDto extractResult = extractSessionLogsFromFile(logFile, sessionId);

                if (!extractResult.getLogs().isEmpty()) {
                    sessionLogs.append(extractResult.getLogs());
                    foundSession = true;

                    if (startTime == null || (extractResult.getStartTime() != null && extractResult.getStartTime().isBefore(startTime))) {
                        startTime = extractResult.getStartTime();
                    }

                    if (endTime == null || (extractResult.getEndTime() != null && extractResult.getEndTime().isAfter(endTime))) {
                        endTime = extractResult.getEndTime();
                    }
                }
            } catch (Exception e) {
                log.error("Error processing log file: {}", logFile, e);
            }
        }

        if (!foundSession) {
            return new SessionLogResultDto("No logs found for session ID: " + sessionId, null, null);
        }

        return new SessionLogResultDto(sessionLogs.toString(), startTime, endTime);
    }

    /**
     * Get all log files (current and archived) from the logs directory
     * @return List of Path objects for all log files
     */
    private List<Path> getAllLogFiles() {
        try {
            Path logsDir = Paths.get(proxyLogsDir);

            if (!Files.exists(logsDir)) {
                log.warn("Logs directory does not exist: {}", logsDir);
                return Collections.emptyList();
            }

            return Files.list(logsDir)
                    .filter(path -> !Files.isDirectory(path))
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.endsWith(".log") || name.endsWith(".log.gz");
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error getting log files from directory: {}", proxyLogsDir, e);
            return Collections.emptyList();
        }
    }

    /**
     * Extract logs for a specific session from a log file
     * @param logFile Path to the log file
     * @param targetSessionId The session ID to extract logs for
     * @return SessionLogExtractResult containing the session logs and timestamps
     */
    private SessionLogResultDto extractSessionLogsFromFile(Path logFile, String targetSessionId) throws IOException {
        StringBuilder sessionLogs = new StringBuilder();
        boolean inTargetSession = false;
        LocalDateTime sessionStartTime = null;
        LocalDateTime sessionEndTime = null;
        String lastLogLine = null;

        try (BufferedReader reader = getLogFileReader(logFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(SESSION_START_PATTERN)) {
                    int startIndex = line.indexOf(SESSION_START_PATTERN) + SESSION_START_PATTERN.length();
                    int endIndex = line.length();

                    String sessionId = line.substring(startIndex, endIndex).trim();

                    if (inTargetSession && !sessionId.equals(targetSessionId)) {
                        sessionEndTime = extractTimestampFromLogLine(lastLogLine);
                        break;
                    }

                    if (sessionId.equals(targetSessionId)) {
                        inTargetSession = true;
                        sessionStartTime = extractTimestampFromLogLine(line);
                    }
                }

                if (inTargetSession) {
                    sessionLogs.append(line).append("\n");
                    lastLogLine = line;
                }
            }
            if (inTargetSession && sessionEndTime == null) {
                sessionEndTime = extractTimestampFromLogLine(lastLogLine);
            }
        }

        return new SessionLogResultDto()
                .setLogs(sessionLogs.toString())
                .setStartTime(sessionStartTime)
                .setEndTime(sessionEndTime);
    }

    /**
     * Get a BufferedReader for a log file (supporting both plain text and gzipped)
     * @param logFile Path to the log file
     * @return BufferedReader for the log file
     * @throws IOException If an I/O error occurs
     */
    private BufferedReader getLogFileReader(Path logFile) throws IOException {
        if (logFile.toString().endsWith(".gz")) {
            return new BufferedReader(new InputStreamReader(
                    new GZIPInputStream(Files.newInputStream(logFile))));
        } else {
            return Files.newBufferedReader(logFile);
        }
    }

    /**
     * Extract timestamp from a log line
     * @param logLine The log line
     * @return LocalDateTime representing the timestamp, or null if not found
     */
    private LocalDateTime extractTimestampFromLogLine(String logLine) {
        if (logLine == null || logLine.length() < 23) {
            return null;
        }

        try {
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("(\\d{4}-\\d{2}-\\d{2}\\s\\d{2}:\\d{2}:\\d{2}\\.\\d{3})")
                    .matcher(logLine);

            if (matcher.find()) {
                return LocalDateTime.parse(matcher.group(1), LOG_DATE_FORMAT);
            }

            return LocalDateTime.parse(logLine.substring(0, 23), LOG_DATE_FORMAT);
        } catch (Exception e) {
            return null;
        }
    }
}
