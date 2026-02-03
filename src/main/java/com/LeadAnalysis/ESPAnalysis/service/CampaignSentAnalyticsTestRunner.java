package com.LeadAnalysis.ESPAnalysis.service;

import com.LeadAnalysis.ESPAnalysis.service.CampaignSentAnalyticsService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@SpringBootApplication
@ComponentScan(basePackages = "com.LeadAnalysis.ESPAnalysis")
public class CampaignSentAnalyticsTestRunner {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("CAMPAIGN SENT ANALYTICS SERVICE TEST");
        System.out.println("========================================\n");

        // Start Spring context
        ApplicationContext context = SpringApplication.run(CampaignSentAnalyticsTestRunner.class, args);
        CampaignSentAnalyticsService service = context.getBean(CampaignSentAnalyticsService.class);

        try {
            // Test 1: Single date test
            runSingleDateTest(service);

//            // Test 2: Date range test (last 7 days)
//            runDateRangeTest(service);
//
//            // Test 3: Monthly test (August 2025)
//            runMonthlyTest(service);
//
//            // Test 4: Custom date test
//            runCustomDateTest(service);

        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Shutdown Spring context
            SpringApplication.exit(context, () -> 0);
        }
    }

    private static void runSingleDateTest(CampaignSentAnalyticsService service) {
        System.out.println("=== TEST 1: SINGLE DATE (Yesterday) ===");

        String testDate = LocalDate.now().minusDays(1).format(DATE_FORMATTER);
        System.out.println("Testing date: " + testDate);

        long startTime = System.currentTimeMillis();
        String result = service.analyzeCampaignSentByDateRange(testDate, testDate);
        long endTime = System.currentTimeMillis();

        System.out.println("\nRESULT:");
        System.out.println(result);
        System.out.println("Time taken: " + (endTime - startTime) / 1000.0 + " seconds");

        checkExcelFile(service);
        System.out.println("\n" + "=".repeat(50) + "\n");
    }

    private static void runDateRangeTest(CampaignSentAnalyticsService service) {
        System.out.println("=== TEST 2: DATE RANGE (Last 7 Days) ===");

        LocalDate endDate = LocalDate.now().minusDays(1);
        LocalDate startDate = endDate.minusDays(6);

        String fromDate = startDate.format(DATE_FORMATTER);
        String toDate = endDate.format(DATE_FORMATTER);

        System.out.println("From: " + fromDate + " To: " + toDate);

        long startTime = System.currentTimeMillis();
        String result = service.analyzeCampaignSentByDateRange(fromDate, toDate);
        long endTime = System.currentTimeMillis();

        System.out.println("\nRESULT:");
        System.out.println(result);
        System.out.println("Time taken: " + (endTime - startTime) / 1000.0 + " seconds");

        checkExcelFile(service);
        System.out.println("\n" + "=".repeat(50) + "\n");
    }

    private static void runMonthlyTest(CampaignSentAnalyticsService service) {
        System.out.println("=== TEST 3: MONTHLY TEST (August 2025) ===");

        String fromDate = "01-08-2025";
        String toDate = "31-08-2025";

        System.out.println("Testing entire month: " + fromDate + " to " + toDate);

        long startTime = System.currentTimeMillis();
        String result = service.analyzeCampaignSentByDateRange(fromDate, toDate);
        long endTime = System.currentTimeMillis();

        System.out.println("\nRESULT:");
        System.out.println(result);
        System.out.println("Time taken: " + (endTime - startTime) / 1000.0 + " seconds");

        checkExcelFile(service);
        System.out.println("\n" + "=".repeat(50) + "\n");
    }

    private static void runCustomDateTest(CampaignSentAnalyticsService service) {
        System.out.println("=== TEST 4: CUSTOM DATES ===");

        // Modify these dates based on your data availability
        String fromDate = "15-08-2025";
        String toDate = "20-08-2025";

        System.out.println("From: " + fromDate + " To: " + toDate);
        System.out.println("NOTE: Modify these dates in the code based on your campaign data availability");

        long startTime = System.currentTimeMillis();
        String result = service.analyzeCampaignSentByDateRange(fromDate, toDate);
        long endTime = System.currentTimeMillis();

        System.out.println("\nRESULT:");
        System.out.println(result);
        System.out.println("Time taken: " + (endTime - startTime) / 1000.0 + " seconds");

        checkExcelFile(service);
        System.out.println("\n" + "=".repeat(50) + "\n");
    }

    private static void checkExcelFile(CampaignSentAnalyticsService service) {
        File excelFile = service.getLatestCampaignSentExcelFile();
        if (excelFile != null && excelFile.exists()) {
            System.out.println("\nEXCEL FILE GENERATED:");
            System.out.println("Path: " + excelFile.getAbsolutePath());
            System.out.println("Size: " + formatFileSize(excelFile.length()));
            System.out.println("Sheet: campaign_level_sent");
            System.out.println("Columns: Campaign Name | emails_sent_count");
        } else {
            System.out.println("\nWARNING: Excel file not generated or not found");
        }
    }

    private static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}