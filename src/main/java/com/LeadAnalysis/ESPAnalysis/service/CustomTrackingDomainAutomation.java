package com.LeadAnalysis.ESPAnalysis.service;

import com.LeadAnalysis.ESPAnalysis.config.API;
import io.restassured.response.Response;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;

// NOTE: You need to have the following dependencies in your pom.xml (or equivalent):
// 1. Apache POI (poi-ooxml) for Excel handling
// 2. RestAssured for API calls
// 3. org.json (or similar) for JSON processing

/**
 * Automates reading domain names from an Excel file, calling two-step APIs,
 * and writing the result back to the sheet. Includes retry logic to handle rate limiting.
 */
public class CustomTrackingDomainAutomation {

    // --- Configuration Constants ---
    private static final String EXCEL_FILE_PATH = "C:\\Users\\saira\\Downloads\\Custom_Tracking_Domain_Removal.xlsx";
    private static final String SHEET_NAME = "Sheet1";
    // Assuming API and BASE_URL are available in the imported API class
    // NOTE: Replace 'API.BASE_URL' and 'API.API_KEY' with actual values or constants if API class is not available.
    private static final String BASE_URL = API.BASE_URL; // Replace with your actual base URL
    private static final String API_Key = API.API_KEY; // Replace with your actual key
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_WAIT_MS = 500;
    private static final int MIN_ACCOUNTS_REQUIRED = 3;
    private static final int RESULT_COLUMN_INDEX = 2; // Column C (0-based index) for writing True/False result

    public static void main(String[] args) {
        processDataAndCallAPI();
    }

    /**
     * Reads the Excel data row by row, processes the domains, and executes the API calls.
     */
    public static void processDataAndCallAPI() {
        System.out.println("Starting Excel data processing for API calls...");

        try (FileInputStream fis = new FileInputStream(EXCEL_FILE_PATH);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheet(SHEET_NAME);
            if (sheet == null) {
                System.err.println("Error: Sheet named '" + SHEET_NAME + "' not found in the workbook.");
                return;
            }

            int lastRowNum = sheet.getLastRowNum();
            int startRow = 1; // Start from row 0

            int rowCount = 0;

            for (int i = startRow; i <= lastRowNum; i++) {
                Row currentRow = sheet.getRow(i);

                if (currentRow == null || currentRow.getPhysicalNumberOfCells() == 0) {
                    continue;
                }

                rowCount++;

                // Read the domain directly from Column A (index 0)
                String domain = getCellValue(currentRow.getCell(0));

                // --- Step 1: Data Validation ---
                if (domain.isEmpty()) {
                    System.out.println("Skipping row " + i + ": Empty Domain.");
                    continue;
                }

                System.out.println("\n--- Processing Row Index: " + i + " | Domain: " + domain + " ---");

                // --- Step 2: Call /account/list API to search for accounts ---
                boolean success = processDomainAccounts(domain);

                // --- Step 3: Write the result back to the Excel sheet ---
                writeResultToSheet(currentRow, RESULT_COLUMN_INDEX, success);
            }

            // --- Step 4: Save the workbook ---
            try (FileOutputStream fos = new FileOutputStream(EXCEL_FILE_PATH)) {
                workbook.write(fos);
                System.out.println("\nSuccessfully processed " + rowCount + " data rows. Results saved to Excel.");
            } catch (IOException e) {
                System.err.println("Error writing to Excel file: " + e.getMessage());
            }

        } catch (FileNotFoundException ex) {
            System.err.println("Error: The file was not found at the specified path: " + EXCEL_FILE_PATH);
            ex.printStackTrace();
        } catch (IOException ex) {
            System.err.println("Error reading the Excel workbook.");
            ex.printStackTrace();
        } catch (Exception ex) {
            System.err.println("An unexpected error occurred during processing.");
            ex.printStackTrace();
        }
    }

    /**
     * Executes the two-step API logic for a single domain.
     */
    private static boolean processDomainAccounts(String domain) {
        // --- API 1: List Accounts (Search by Domain) ---
        Map<String, Object> listPayload = new HashMap<>();
        listPayload.put("search", domain);
        listPayload.put("limit", 15);
        listPayload.put("filter", null);
        listPayload.put("skip", 0);
        listPayload.put("include_tags", true);
        listPayload.put("sort_options", null);

        Response listResponse = callApiWithRetry("/backend-alt/api/v1/account/list", listPayload, true);

        if (listResponse == null || listResponse.getStatusCode() != 200) {
            System.err.println("API 1 failed or returned non-200 status.");
            return false;
        }

        // --- Response Filtering ---
        JSONArray accounts;
        try {
            JSONObject jsonResponse = new JSONObject(listResponse.getBody().asString());
            accounts = jsonResponse.getJSONArray("accounts");
        } catch (Exception e) {
            System.err.println("Error parsing list response JSON: " + e.getMessage());
            return false;
        }

        int matchedCount = 0;
        int updateSuccessCount = 0;

        for (int i = 0; i < accounts.length(); i++) {
            JSONObject account = accounts.getJSONObject(i);
            String accountEmail = account.getString("email");

            // Validate the email retrieved from API matches the domain from the sheet
            if (accountEmail.endsWith("@" + domain)) {
                matchedCount++;

                // We only start updating accounts once the minimum required count is met.
                if (matchedCount >= 0) {
                    System.out.println("-> Matched Email (" + matchedCount + "): " + accountEmail);

                    // --- API 2: Call /account/update API to clear CTD ---
                    Map<String, Object> updatePayload = new HashMap<>();
                    updatePayload.put("c_t_domain", null); // Set to null to clear CTD
                    updatePayload.put("email", accountEmail);

                    // The second API call is also a POST, passing false as the third parameter to
                    // callApiWithRetry is only to indicate that it's a different call type for internal logic,
                    // but since both are POST in this case, the flag is not strictly necessary but kept for consistency.
                    Response updateResponse = callApiWithRetry("/backend/api/v1/account/update", updatePayload, false);

                    if (updateResponse != null && updateResponse.getStatusCode() == 200 &&
                            "success".equals(updateResponse.jsonPath().get("status"))) {
                        updateSuccessCount++;
                        System.out.println("   -> Update successful: Cleared CTD for " + accountEmail);
                    } else {
                        System.err.println("   -> Update FAILED for " + accountEmail);
                    }
                }
            }
        }

        if (matchedCount < 0) {
            System.err.println("REQUIRED FAILURE: Only " + matchedCount + " matching accounts found. Skipping update phase.");
            return false;
        }

        System.out.println("Total matching accounts processed: " + matchedCount);
        System.out.println("Total successful CTD updates: " + updateSuccessCount);

        // Return true if the minimum account requirement was met and at least one update succeeded
        return updateSuccessCount > 0;
    }

    /**
     * Helper method to call an API endpoint with built-in retry logic.
     * @param endpoint The API path to call (e.g., "/backend-alt/api/v1/account/list").
     * @param payload The body of the request.
     * @param isPost boolean flag indicating if the request is a POST. (Used to distinguish between the two calls, but both are POST here).
     * @return The RestAssured Response object, or null on final failure.
     */
    private static Response callApiWithRetry(String endpoint, Map<String, Object> payload, boolean isPost) {
        Response response = null;
        int attempt = 0;

        while (attempt < MAX_RETRIES) {
            attempt++;
            try {
                System.out.println("Attempt " + attempt + " to call " + endpoint);

                // Both API calls are POST requests, as per the user's provided structure
                response = given()
                        .baseUri(BASE_URL)
                        .header("X-Org-Auth", API_Key)
                        .contentType("application/json") // Ensure Content-Type is set for JSON
                        .body(payload)
                        .when()
                        .post(endpoint);

                int statusCode = response.getStatusCode();

                // Success: 2xx (or 3xx redirect, but typically 200-204)
                // We retry only on 429 (Rate Limit) or 5xx (Server Error)
                if (statusCode >= 200 && statusCode < 400) {
                    return response; // Success
                }

                // Retry if rate-limited (429) or server error (5xx)
                if (statusCode == 429 || statusCode >= 500) {
                    System.out.println("API call received status " + statusCode + ". Retrying in " + RETRY_WAIT_MS + "ms.");
                    TimeUnit.MILLISECONDS.sleep(RETRY_WAIT_MS);
                } else {
                    // Non-retriable error (e.g., 400 Bad Request, 401 Unauthorized)
                    System.err.println("Non-retriable API error: Status " + statusCode + " on attempt " + attempt + ".");
                    return response; // Return the response to handle the specific error outside the loop
                }

            } catch (Exception e) {
                System.err.println("Exception during API call: " + e.getMessage() + ". Retrying...");
                try {
                    TimeUnit.MILLISECONDS.sleep(RETRY_WAIT_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        System.err.println("API call failed after " + MAX_RETRIES + " attempts for endpoint: " + endpoint);
        return response; // Return final failed response
    }


    /**
     * Helper method to safely extract a String value from a cell.
     * Uses DataFormatter for robust conversion, assuming the required output is always a String.
     * @param cell The Excel cell to read.
     * @return The cell value as a String.
     */
    private static String getCellValue(Cell cell) {
        if (cell == null) {
            return "";
        }

        DataFormatter formatter = new DataFormatter();
        return formatter.formatCellValue(cell).trim();
    }

    /**
     * Writes the boolean result (True/False) to a specific column in the current row.
     * @param row The current Excel row object.
     * @param colIndex The column index (0-based) to write to.
     * @param result The boolean result (true for success, false for failure).
     */
    private static void writeResultToSheet(Row row, int colIndex, boolean result) {
        Cell resultCell = row.getCell(colIndex, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        resultCell.setCellValue(result ? "True" : "False");
    }
}
