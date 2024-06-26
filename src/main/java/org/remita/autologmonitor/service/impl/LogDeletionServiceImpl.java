package org.remita.autologmonitor.service.impl;

import jakarta.mail.MessagingException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.remita.autologmonitor.dto.MailResponseDto;
import org.remita.autologmonitor.service.EmailSenderService;
import org.remita.autologmonitor.service.LogDeletionService;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service @Slf4j @AllArgsConstructor
public class LogDeletionServiceImpl implements LogDeletionService {
    private static final String logDirectory = "log";
    private final EmailSenderService emailSenderService;

    @Override public void deleteLogs() throws MessagingException {
        System.out.println("Checking log directory: " + logDirectory);
        boolean processComplete = loopThroughLogDirectory();
        sendMailToDevOps(processComplete);
    }

    private void sendMailToDevOps(boolean processComplete) throws MessagingException {
        MailResponseDto responseDto = new MailResponseDto();
        responseDto.setTitle("Log Deletion Service");
        responseDto.setEmail("taiwoh782@gmail.com");
        responseDto.setSubject("Deletion of Logs older than 7 days.");
        responseDto.setBody("Logs have been successfully deleted");

        if (processComplete) {
            emailSenderService.sendMail(responseDto);
        }else{
            responseDto.setBody("Error processing log files");
            emailSenderService.sendMail(responseDto);
        }
    }

    private boolean loopThroughLogDirectory() {
        ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        File dir = new File(logDirectory);
        File[] directoryListing = dir.listFiles();
        if (directoryListing != null) {
            for (File log : directoryListing) {
                executorService.submit(() -> {
                    try {
                        performLogCheckOnFile("log/" + log.getName());
                    } catch (IOException e) {
                        throw new RuntimeException(e.getMessage());
                    }
                });
            }
            executorService.shutdown();
            if(executorService.isShutdown()){
                return true;
            }
        } else {
            log.info("Error Reading Log File");
        }
        return false;
    }

    private void performLogCheckOnFile(String fileName) throws IOException{
        File logFile = new File(fileName);
        File tempFile = new File(UUID.randomUUID() + ".log");

        try (
                BufferedReader reader = new BufferedReader(new FileReader(logFile));
                BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))
        ) {
            String line;
            String lastCurrentTimestamp = "";
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                List<String> logs = new ArrayList<>();
                logs.add(line);
                try {
                    String timeStamp =  "";
                    for(String log : logs){
                        if(log.equals("")) continue;
                        if(isTimestampLine(log)){
                            timeStamp = extractTimestamp(log);
                            lastCurrentTimestamp = timeStamp;
                            if (!checkIfTimeStampIsOverdue(timeStamp) && !log.isEmpty() && !isErrorOrIrrelevant(log)) {
                                writer.write(log);
                                writer.newLine();
                            }
                        }else{
                            if (!checkIfTimeStampIsOverdue(lastCurrentTimestamp)) {
                                writer.write(log);
                                writer.newLine();
                            }
                        }
                    }
                } catch (Exception e) {
                    log.info("Timestamp format error: {}", e.getMessage());
                }
            }
        } catch (IOException e) {
            log.info("An error occurred while reading log file: {}", e.getMessage());
        }

        try {
            Files.delete(logFile.toPath());
            Files.move(tempFile.toPath(), logFile.toPath());
        } catch (IOException e) {
            log.info("An error occurred while replacing file: {}", e.getMessage());
        }
    }

    private static boolean checkIfTimeStampIsOverdue(String timeStamp) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate timeStampDate = LocalDate.parse(timeStamp, formatter);
        LocalDate currentDate = LocalDate.now();

        return timeStampDate.isBefore(currentDate.minusDays(7));
    }

    private static boolean isTimestampLine(String line) {
        return line.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\+\\d{2}:\\d{2}.*");
    }

    private static String extractTimestamp(String line) {
        return line.split(" ")[0].split("T")[0];
    }

    private boolean isErrorOrIrrelevant(String line) {
        return line.startsWith("***************************") ||
                line.startsWith("APPLICATION FAILED TO START") ||
                line.startsWith("Description") ||
                line.contains("Parameter") && line.contains("of constructor") && line.contains("required a bean") && line.contains("that could not be found") ||
                line.contains("org.") || line.contains("jakarta.") ||
                line.contains("java.base/") ||
                line.contains("Caused by") ||
                line.contains("common frames") || line.startsWith("LogError")
                || line.contains("Consider defining")
                || line.contains("Action");
    }
}
