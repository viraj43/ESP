
package com.LeadAnalysis.ESPAnalysis.controller;
import com.LeadAnalysis.ESPAnalysis.service.EmailLevelTouchAnalysisService;
import com.LeadAnalysis.ESPAnalysis.service.EspAnalyzerService;
import com.LeadAnalysis.ESPAnalysis.service.LeadAnalyzerService;
import com.LeadAnalysis.ESPAnalysis.service.EventTypeAnalyzerService;
import com.LeadAnalysis.ESPAnalysis.service.PrimaryRepliesAnalyzerService;
import com.LeadAnalysis.ESPAnalysis.service.OtherRepliesAnalyzerService;
import com.LeadAnalysis.ESPAnalysis.service.CampaignLeadAnalyzerService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import com.LeadAnalysis.ESPAnalysis.service.CampaignRepliesAnalyzerService;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import com.LeadAnalysis.ESPAnalysis.service.CampaignSentAnalyticsService;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Controller
public class EspAnalyzerController {

    // Fixed: Use Map<String, Map<String, String>> for simple session management
    private Map<String, Map<String, String>> analysisSessions = new ConcurrentHashMap<>();

    @Autowired
    private EspAnalyzerService espAnalyzerService;

    @Autowired
    private CampaignSentAnalyticsService campaignSentAnalyticsService;

    @Autowired
    private LeadAnalyzerService leadAnalyzerService;

    @Autowired
    private EventTypeAnalyzerService eventTypeAnalyzerService;

    @Autowired
    private PrimaryRepliesAnalyzerService primaryRepliesAnalyzerService;

    @Autowired
    private OtherRepliesAnalyzerService otherRepliesAnalyzerService;

    @Autowired
    private CampaignLeadAnalyzerService campaignLeadAnalyzerService;

    @Autowired
    private CampaignRepliesAnalyzerService campaignRepliesAnalyzerService;

    // Login page
    @GetMapping("/login")
    public String login() {
        return "login";
    }

    // Home page with analysis options (after login)
    @GetMapping("/")
    public String index(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        String email = auth.getName();
        String role = auth.getAuthorities().iterator().next().getAuthority();

        model.addAttribute("userEmail", email);
        model.addAttribute("userRole", role);
        return "home";
    }

    // Lead Analysis Page
    @GetMapping("/lead-analysis")
    public String leadAnalysis(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        model.addAttribute("userEmail", auth.getName());
        return "lead-analysis";
    }

    // Combined Replies Analysis Page (keep for backward compatibility)
    @GetMapping("/replies-analysis")
    public String repliesAnalysis(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        model.addAttribute("userEmail", auth.getName());
        return "replies-analysis";
    }

    // Primary Replies Analysis Page
    @GetMapping("/primary-replies-analysis")
    public String primaryRepliesAnalysis(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        model.addAttribute("userEmail", auth.getName());
        return "primary-replies-analysis";
    }

    // Other Replies Analysis Page
    @GetMapping("/other-replies-analysis")
    public String otherRepliesAnalysis(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        model.addAttribute("userEmail", auth.getName());
        return "other-replies-analysis";
    }

    // Event Type Analysis Page
    @GetMapping("/event-type-analysis")
    public String eventTypeAnalysis(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        model.addAttribute("userEmail", auth.getName());
        return "event-type-analysis";
    }

    // Campaign Lead Analysis Page
    @GetMapping("/campaign-lead-analysis")
    public String campaignLeadAnalysis(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        model.addAttribute("userEmail", auth.getName());
        return "campaign-lead-analysis";
    }

    // Lead Analysis API endpoint
    @PostMapping("/analyze-leads")
    @ResponseBody
    public String analyzeLeads(@RequestParam int leadCount) {
        try {
            String result = leadAnalyzerService.analyzeLeadESP(leadCount);
            return result;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    // Combined Replies Analysis API endpoint (keep for backward compatibility)
    @PostMapping("/analyze")
    @ResponseBody
    public String analyzeEmails(@RequestParam String fromDate,
                                @RequestParam String toDate) {
        try {
            String result = espAnalyzerService.analyzeESPByDateRange(fromDate, toDate);
            return result;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    // Primary Replies Analysis API endpoint
    @PostMapping("/analyze-primary-replies")
    @ResponseBody
    public String analyzePrimaryReplies(@RequestParam String fromDate,
                                        @RequestParam String toDate,
                                        @RequestParam(defaultValue = "true") boolean includeEspAnalysis) {
        try {
            return primaryRepliesAnalyzerService.analyzePrimaryRepliesByDateRange(fromDate, toDate, includeEspAnalysis);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
    // Other Replies Analysis API endpoint
    @PostMapping("/analyze-other-replies")
    @ResponseBody
    public String analyzeOtherReplies(@RequestParam String fromDate,
                                      @RequestParam String toDate) {
        System.out.println("🔍 Other Replies endpoint called with dates: " + fromDate + " to " + toDate);
        try {
            String result = otherRepliesAnalyzerService.analyzeOtherRepliesByDateRange(fromDate, toDate);
            return result;
        } catch (Exception e) {
            System.err.println("❌ Error in other replies analysis: " + e.getMessage());
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }

    // Event Type Analysis API endpoint
    @PostMapping("/analyze-event-types")
    @ResponseBody
    public String analyzeEventTypes(@RequestParam String fromDate,
                                    @RequestParam String toDate) {
        try {
            String result = eventTypeAnalyzerService.analyzeEventTypeByDateRange(fromDate, toDate);
            return result;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    // Campaign Lead Analysis API endpoint
    @PostMapping("/analyze-campaign-leads")
    @ResponseBody
    public String analyzeCampaignLeads(@RequestParam String fromDate,
                                       @RequestParam String toDate) {
        try {
            String result = campaignLeadAnalyzerService.analyzeCampaignLeadsByDateRange(fromDate, toDate);
            return result;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    // Download Lead Analysis Excel
    @GetMapping("/download-lead")
    public ResponseEntity<Resource> downloadLeadExcel() {
        try {
            File file = leadAnalyzerService.getLatestLeadExcelFile();
            if (file == null || !file.exists()) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(file);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = "Lead_ESP_Analysis_Report_" + timestamp + ".xlsx";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Download Combined Replies Analysis Excel (keep for backward compatibility)
    @GetMapping("/download")
    public ResponseEntity<Resource> downloadExcel() {
        try {
            File file = espAnalyzerService.getLatestExcelFile();
            if (file == null || !file.exists()) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(file);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = "Replies_ESP_Analysis_Report_" + timestamp + ".xlsx";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Download Primary Replies Analysis Excel
    @GetMapping("/download-primary-replies")
    public ResponseEntity<Resource> downloadPrimaryRepliesExcel() {
        try {
            File file = primaryRepliesAnalyzerService.getLatestPrimaryRepliesExcelFile();
            if (file == null || !file.exists()) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(file);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = "Primary_Replies_Analysis_Report_" + timestamp + ".xlsx";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Download Other Replies Analysis Excel
    @GetMapping("/download-other-replies")
    public ResponseEntity<Resource> downloadOtherRepliesExcel() {
        try {
            File file = otherRepliesAnalyzerService.getLatestOtherRepliesExcelFile();
            if (file == null || !file.exists()) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(file);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = "Other_Replies_Analysis_Report_" + timestamp + ".xlsx";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Download Event Type Analysis Excel
    @GetMapping("/download-event-type")
    public ResponseEntity<Resource> downloadEventTypeExcel() {
        try {
            File file = eventTypeAnalyzerService.getLatestEventTypeExcelFile();
            if (file == null || !file.exists()) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(file);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = "Event_Type_Analysis_Report_" + timestamp + ".xlsx";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Download Campaign Lead Analysis Excel
    @GetMapping("/download-campaign-lead")
    public ResponseEntity<Resource> downloadCampaignLeadExcel() {
        try {
            File file = campaignLeadAnalyzerService.getLatestCampaignLeadExcelFile();
            if (file == null || !file.exists()) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(file);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = "Campaign_Lead_Analysis_Report_" + timestamp + ".xlsx";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    //Campaign Replies Analysis Page
    @GetMapping("/campaign-replies-analysis")
    public String campaignRepliesAnalysis(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        model.addAttribute("userEmail", auth.getName());
        return "campaign-replies-analysis";
    }

    // Campaign Replies Analysis API endpoint (original - still works)
    @PostMapping("/analyze-campaign-replies")
    @ResponseBody
    public String analyzeCampaignReplies(@RequestParam String fromDate,
                                         @RequestParam String toDate) {
        try {
            String result = campaignRepliesAnalyzerService.analyzeCampaignRepliesByDateRange(fromDate, toDate);
            return result;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    // Download Campaign Replies Analysis Excel
    @GetMapping("/download-campaign-replies")
    public ResponseEntity<Resource> downloadCampaignRepliesExcel() {
        try {
            File file = campaignRepliesAnalyzerService.getLatestCampaignRepliesExcelFile();
            if (file == null || !file.exists()) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(file);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = "Campaign_Replies_Analysis_Report_" + timestamp + ".xlsx";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // NEW: Enhanced streaming endpoints for real-time progress monitoring

    @PostMapping("/start-campaign-replies-analysis")
    @ResponseBody
    public Map<String, String> startCampaignRepliesAnalysis(@RequestParam String fromDate, @RequestParam String toDate) {
        String sessionId = UUID.randomUUID().toString();

        // Store session data as simple string map
        Map<String, String> sessionData = new HashMap<>();
        sessionData.put("fromDate", fromDate);
        sessionData.put("toDate", toDate);
        sessionData.put("status", "started");
        sessionData.put("startTime", String.valueOf(System.currentTimeMillis()));

        analysisSessions.put(sessionId, sessionData);

        Map<String, String> response = new HashMap<>();
        response.put("sessionId", sessionId);
        response.put("status", "started");

        return response;
    }
    // Add this new page mapping method
    @GetMapping("/campaign-sent-analytics")
    public String campaignSentAnalytics(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        model.addAttribute("userEmail", auth.getName());
        return "campaign-sent-analytics";
    }

    // Add this API endpoint for analysis
    @PostMapping("/analyze-campaign-sent")
    @ResponseBody
    public String analyzeCampaignSent(@RequestParam String fromDate,
                                      @RequestParam String toDate) {
        try {
            String result = campaignSentAnalyticsService.analyzeCampaignSentByDateRange(fromDate, toDate);
            return result;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    // Add this download endpoint
    @GetMapping("/download-campaign-sent")
    public ResponseEntity<Resource> downloadCampaignSentExcel() {
        try {
            File file = campaignSentAnalyticsService.getLatestCampaignSentExcelFile();
            if (file == null || !file.exists()) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(file);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = "Campaign_Sent_Analytics_Report_" + timestamp + ".xlsx";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping(value = "/campaign-replies-stream/{sessionId}", produces = "text/event-stream")
    public ResponseEntity<StreamingResponseBody> streamCampaignRepliesAnalysis(@PathVariable String sessionId,
                                                                               HttpServletRequest request) {
        System.out.println("=== STREAMING ANALYSIS REQUEST STARTED ===");
        System.out.println("Session ID: " + sessionId);
        System.out.println("Thread: " + Thread.currentThread().getName());
        System.out.println("Timestamp: " + java.time.LocalDateTime.now());

        StreamingResponseBody stream = outputStream -> {
            ScheduledExecutorService heartbeatExecutor = null;
            ScheduledFuture<?> heartbeat = null;
            boolean streamActive = true;

            try {
                System.out.println("=== STREAM LAMBDA EXECUTION STARTED ===");
                System.out.println("Starting streaming analysis for session: " + sessionId);
                System.out.println("OutputStream type: " + outputStream.getClass().getSimpleName());

                // Test initial SSE connection
                System.out.println("Attempting to send initial connection message...");
                boolean connectionSuccess = sendSSEMessage(outputStream, "connected", "Stream connected successfully");
                System.out.println("Initial connection message result: " + connectionSuccess);

                if (!connectionSuccess) {
                    System.err.println("FATAL: Failed to send initial SSE message, aborting stream");
                    streamActive = false;
                    return;
                }

                // Session validation with detailed logging
                System.out.println("=== SESSION VALIDATION ===");
                System.out.println("Total sessions in map: " + analysisSessions.size());
                System.out.println("Session keys: " + analysisSessions.keySet());

                Map<String, String> session = analysisSessions.get(sessionId);
                System.out.println("Retrieved session for " + sessionId + ": " + (session != null ? "FOUND" : "NULL"));

                if (session == null) {
                    System.err.println("ERROR: Session not found for sessionId: " + sessionId);
                    sendSSEMessage(outputStream, "error", "Session not found");
                    streamActive = false;
                    return;
                }

                // Extract session data with validation
                String fromDate = session.get("fromDate");
                String toDate = session.get("toDate");
                System.out.println("Session data - fromDate: " + fromDate + ", toDate: " + toDate);

                if (fromDate == null || toDate == null) {
                    System.err.println("ERROR: Invalid session data - fromDate or toDate is null");
                    sendSSEMessage(outputStream, "error", "Invalid session data");
                    streamActive = false;
                    return;
                }

                // Setup heartbeat with timeout awareness
                System.out.println("=== HEARTBEAT SETUP ===");
                try {
                    heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
                    final boolean[] connectionAlive = {true};

                    heartbeat = heartbeatExecutor.scheduleAtFixedRate(() -> {
                        if (connectionAlive[0]) {
                            try {
                                boolean heartbeatSuccess = sendSSEMessage(outputStream, "heartbeat", "alive");
                                if (!heartbeatSuccess) {
                                    System.out.println("Heartbeat failed - connection likely closed");
                                    connectionAlive[0] = false;
                                }
                            } catch (Exception e) {
                                System.err.println("Heartbeat exception: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                                connectionAlive[0] = false;
                            }
                        }
                    }, 15, 15, TimeUnit.SECONDS); // Reduced frequency to avoid spam
                    System.out.println("Heartbeat scheduled successfully");
                } catch (Exception e) {
                    System.err.println("Failed to setup heartbeat: " + e.getMessage());
                    e.printStackTrace();
                }

                // Send estimation message
                sendSSEMessage(outputStream, "status", "Analysis started - estimated duration: 5-15 minutes depending on data volume");

                try {
                    System.out.println("=== STARTING CAMPAIGN ANALYSIS SERVICE ===");
                    System.out.println("Service class: " + campaignRepliesAnalyzerService.getClass().getSimpleName());
                    System.out.println("Parameters - fromDate: " + fromDate + ", toDate: " + toDate + ", sessionId: " + sessionId);

                    // Start the analysis with streaming
                    campaignRepliesAnalyzerService.analyzeCampaignRepliesWithStreaming(
                            fromDate, toDate, outputStream, sessionId
                    );

                    System.out.println("Campaign analysis service completed normally");
                    streamActive = false; // Analysis completed successfully

                } catch (Exception serviceException) {
                    System.err.println("=== SERVICE EXCEPTION CAUGHT ===");
                    System.err.println("Exception type: " + serviceException.getClass().getName());
                    System.err.println("Exception message: " + serviceException.getMessage());
                    System.err.println("Exception cause: " + (serviceException.getCause() != null ?
                            serviceException.getCause().getClass().getSimpleName() + " - " + serviceException.getCause().getMessage() : "None"));

                    // Check if this is a timeout-related exception
                    if (serviceException.getMessage() != null &&
                            (serviceException.getMessage().contains("timeout") ||
                                    serviceException.getMessage().contains("async") ||
                                    serviceException.getMessage().contains("Response not usable"))) {
                        System.err.println("DETECTED TIMEOUT-RELATED EXCEPTION");
                    }

                    // Print full stack trace
                    System.err.println("=== FULL STACK TRACE ===");
                    serviceException.printStackTrace();

                    // Try to notify client of the specific error
                    try {
                        String detailedError = "Service error: " + serviceException.getClass().getSimpleName();
                        if (serviceException.getMessage() != null) {
                            detailedError += " - " + serviceException.getMessage();
                        }
                        sendSSEMessage(outputStream, "error", detailedError);
                    } catch (Exception notificationException) {
                        System.err.println("Failed to send error notification: " + notificationException.getMessage());
                    }

                    streamActive = false;
                    throw serviceException; // Re-throw to be caught by outer catch
                }

            } catch (Exception outerException) {
                System.err.println("=== OUTER EXCEPTION CAUGHT ===");
                System.err.println("Outer exception type: " + outerException.getClass().getName());
                System.err.println("Outer exception message: " + outerException.getMessage());

                // Special handling for timeout exceptions
                if (outerException instanceof AsyncRequestTimeoutException ||
                        (outerException.getMessage() != null && outerException.getMessage().contains("timeout"))) {
                    System.err.println("CONFIRMED: This is a timeout exception");
                }

                // Print full stack trace for outer exception
                System.err.println("=== OUTER EXCEPTION STACK TRACE ===");
                outerException.printStackTrace();

                try {
                    String errorMessage = "Stream failed: " + outerException.getClass().getSimpleName();
                    if (outerException.getMessage() != null && !outerException.getMessage().equals("undefined")) {
                        errorMessage += " - " + outerException.getMessage();
                    } else if (outerException.getMessage() != null && outerException.getMessage().equals("undefined")) {
                        errorMessage = "Stream failed with undefined error - check server logs for details";
                        System.err.println("DETECTED 'undefined' ERROR - This indicates a null pointer or missing variable");
                    }
                    sendSSEMessage(outputStream, "error", errorMessage);
                } catch (Exception finalException) {
                    System.err.println("Failed to send final error message: " + finalException.getMessage());
                }

                streamActive = false;

            } finally {
                System.out.println("=== CLEANUP PHASE ===");
                System.out.println("Stream was active: " + streamActive);

                // Cleanup heartbeat
                if (heartbeat != null) {
                    System.out.println("Cancelling heartbeat...");
                    heartbeat.cancel(true);
                }
                if (heartbeatExecutor != null) {
                    System.out.println("Shutting down heartbeat executor...");
                    heartbeatExecutor.shutdown();
                    try {
                        if (!heartbeatExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                            System.out.println("Forcing heartbeat executor shutdown...");
                            heartbeatExecutor.shutdownNow();
                        }
                    } catch (InterruptedException e) {
                        System.err.println("Interrupted while waiting for executor shutdown");
                        heartbeatExecutor.shutdownNow();
                    }
                }

                // Send final messages only if stream is still active
                if (streamActive) {
                    try {
                        System.out.println("Sending disconnection message...");
                        sendSSEMessage(outputStream, "disconnected", "Stream ending");
                        outputStream.flush();
                    } catch (Exception closeException) {
                        System.err.println("Error sending final message: " + closeException.getMessage());
                    }
                }

                // Always try to close the stream
                try {
                    outputStream.close();
                    System.out.println("Stream closed successfully");
                } catch (Exception closeException) {
                    System.err.println("Error closing stream: " + closeException.getMessage());
                }

                // Cleanup session
                try {
                    System.out.println("Removing session: " + sessionId);
                    Map<String, String> removedSession = analysisSessions.remove(sessionId);
                    System.out.println("Session removed: " + (removedSession != null ? "SUCCESS" : "NOT_FOUND"));
                    System.out.println("Remaining sessions: " + analysisSessions.size());
                } catch (Exception sessionException) {
                    System.err.println("Error removing session: " + sessionException.getMessage());
                }

                System.out.println("=== STREAMING ANALYSIS COMPLETED ===");
            }
        };

        System.out.println("Returning StreamingResponseBody...");
        return ResponseEntity.ok()
                .header("Content-Type", "text/event-stream")
                .header("Cache-Control", "no-cache")
                .header("Connection", "keep-alive")
                .header("Access-Control-Allow-Origin", "*")
                .header("X-Accel-Buffering", "no") // Disable nginx buffering
                .body(stream);
    }

    // Helper method for SSE with enhanced logging
    private boolean sendSSEMessage(OutputStream outputStream, String event, String data) {
        try {
            if (outputStream == null) {
                System.err.println("SSE ERROR: OutputStream is null");
                return false;
            }

            if (event == null) {
                System.err.println("SSE WARNING: event is null, using 'message'");
                event = "message";
            }

            if (data == null) {
                System.err.println("SSE WARNING: data is null, using 'null'");
                data = "null";
            }

            String message = "event: " + event + "\ndata: " + data + "\n\n";
            outputStream.write(message.getBytes("UTF-8"));
            outputStream.flush();

            // Log successful SSE messages (only for important events)
            if ("error".equals(event) || "connected".equals(event) || "complete".equals(event)) {
                System.out.println("SSE SENT [" + event + "]: " + data);
            }

            return true;
        } catch (IOException e) {
            System.err.println("SSE IO Error [" + event + "]: " + e.getMessage());
            return false;
        } catch (Exception e) {
            System.err.println("SSE Unexpected Error [" + event + "]: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    @Autowired
    private EmailLevelTouchAnalysisService emailLevelTouchAnalysisService;

    @GetMapping("/api/tags")
    public ResponseEntity<List<EmailLevelTouchAnalysisService.SimpleCustomTag>> getCustomTags() {
        try {
            List<EmailLevelTouchAnalysisService.SimpleCustomTag> tags = emailLevelTouchAnalysisService.fetchAllCustomTags();
            return ResponseEntity.ok(tags);
        } catch (Exception e) {
            // Log the error and return an appropriate status code
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Add this new page mapping method
    @GetMapping("/email-touch-analysis")
    public String emailTouchAnalysis(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        model.addAttribute("userEmail", auth.getName());
        return "email-touch-analysis";
    }

    // Add this API endpoint for analysis
    @PostMapping("/analyze-email-touch")
    @ResponseBody
    public String analyzeEmailTouch(@RequestParam String tagSearch,
                                    @RequestParam String date) {
        try {
            String result = emailLevelTouchAnalysisService.analyzeEmailTouchByTagAndDate(tagSearch, date);
            return result;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    // Add this download endpoint
    @GetMapping("/download-email-touch")
    public ResponseEntity<Resource> downloadEmailTouchExcel() {
        try {
            File file = emailLevelTouchAnalysisService.getLatestEmailTouchExcelFile();
            if (file == null || !file.exists()) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(file);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = "Email_Touch_Analysis_Report_" + timestamp + ".xlsx";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}