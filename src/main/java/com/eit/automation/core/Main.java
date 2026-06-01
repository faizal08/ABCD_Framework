package com.eit.automation.core;

import com.eit.automation.pages.LoginPage;
import com.eit.automation.parser.StepParser;
import com.eit.automation.parser.TestStep;
import com.eit.automation.utils.CSVTestCaseReader;
import com.eit.automation.utils.ReportGenerator;
import com.eit.automation.utils.VideoRecorder;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

public class Main {
    public static Properties config;
    public static TestExecutor executor;
    private static VideoRecorder videoRecorder;

    // Track sheets already completed to avoid infinite loops and double runs
    private static Set<String> executedSheets = new HashSet<>();
    private static boolean isBrowserStarted = false;

    static {
        try {
            videoRecorder = new VideoRecorder();
        } catch (Exception e) {
            System.err.println("❌ Critical: Failed to initialize Video Recorder: " + e.getMessage());
            // You can choose to leave it null; the try-catch in your loop
            // will then handle the null pointer safely.
        }
    }

    public static void main(String[] args) {
        ReportGenerator reportGenerator = new ReportGenerator();

        try {
            System.out.println("=== 🚀 EIT Test Automation Started ===\n");

            // --- IMPROVED DYNAMIC CONFIG LOADING ---
            config = new Properties();
            String env = System.getProperty("env");

            String configFileName = env + ".properties";
            File configFile = new File(configFileName);

            if (!configFile.exists()) {
                System.out.println("⚠️  Config '" + configFileName + "' not found. Falling back to 'config.properties'.");
                configFileName = "config.properties";
                configFile = new File(configFileName);
            }

            System.out.println("🔧 Loading Configuration: " + configFile.getAbsolutePath());
            try (FileInputStream configFis = new FileInputStream(configFile)) {
                config.load(configFis);
            }

            // Validate that we actually loaded data (prevents NullPointerException later)
            String testFilePath = config.getProperty("excel.name");
            if (testFilePath == null || testFilePath.isEmpty()) {
                throw new RuntimeException("❌ Error: 'excel.name' is missing or empty in " + configFileName);
            }
            // --- END CONFIG LOADING ---

            reportGenerator.setExcelFileName(testFilePath);
            reportGenerator.startTestExecution();

            System.out.println("📂 Reading test cases from: " + testFilePath);
            File testFile = new File(testFilePath);
            if (!testFile.exists()) {
                throw new RuntimeException("Test file not found at: " + testFile.getAbsolutePath());
            }

            // Detect file type and read accordingly
            if (testFilePath.toLowerCase().endsWith(".csv")) {
                System.out.println("📄 Detected CSV file format");
                executor = new TestExecutor(reportGenerator,config);
                readCSVTestCases(testFilePath, executor, reportGenerator);
            } else if (testFilePath.toLowerCase().endsWith(".xlsx") || testFilePath.toLowerCase().endsWith(".xls")) {
                System.out.println("📊 Detected Excel file format");
                readExcelTestCases(testFilePath, reportGenerator);
            } else {
                throw new RuntimeException("Unsupported file format. Please use .csv, .xlsx, or .xls files.");
            }

            reportGenerator.endTestExecution();

        } catch (Exception e) {
            System.err.println("❌ Execution failed: " + e.getMessage());
            e.printStackTrace();
            reportGenerator.endTestExecution();
        } finally {
            if (executor != null) {
                System.out.println("🛑 Closing browser...");
                executor.close();
            }
        }
    }
    /**
     * Read test cases from Excel file - UPDATED TO SUPPORT SHEET REPEAT ITERATIONS [X]
     */
    private static void readExcelTestCases(String excelPath, ReportGenerator reportGenerator) throws Exception {
        String sheetNameConfig = config.getProperty("sheets.name");
        String[] rawSheetTokens = (sheetNameConfig != null) ? sheetNameConfig.split(",") : new String[0];

        try (FileInputStream fis = new FileInputStream(excelPath);
             Workbook workbook = new XSSFWorkbook(fis)) {

            if (executor == null) {
                executor = new TestExecutor(reportGenerator, config);
            }

            for (String token : rawSheetTokens) {
                String cleanToken = token.trim();
                if (cleanToken.isEmpty()) continue;

                String sheetName = cleanToken;
                int repeatCount = 1; // Default to 1 if no bracket configuration is provided

                // Extract brackets pattern: e.g., "AddCustomer[50]"
                if (cleanToken.contains("[") && cleanToken.endsWith("]")) {
                    try {
                        sheetName = cleanToken.substring(0, cleanToken.indexOf("[")).trim();
                        String countStr = cleanToken.substring(cleanToken.indexOf("[") + 1, cleanToken.length() - 1).trim();
                        repeatCount = Integer.parseInt(countStr);
                    } catch (Exception e) {
                        System.err.println("⚠️ Warning: Failed to parse iteration count for token '" + cleanToken + "'. Falling back to 1 loop.");
                        sheetName = cleanToken.replace("[", "").replace("]", "").trim();
                        repeatCount = 1;
                    }
                }

                // Run the target sheet the exact number of times requested
                System.out.println("\n🎯 Target Configuration Set: Sheet [" + sheetName + "] will iterate " + repeatCount + " time(s).");
                for (int currentRun = 1; currentRun <= repeatCount; currentRun++) {
                    System.out.println("\n========================================================");
                    System.out.println("🔄 STRESS LOOP ATTEMPT #" + currentRun + " OF " + repeatCount + " FOR SHEET: [" + sheetName + "]");
                    System.out.println("========================================================");

                    // Force-remove the target sheet from execution tracking for this iteration pass
                    // so it bypasses the recursion lock safely
                    executedSheets.remove(sheetName);

                    runSheetWithPrecondition(sheetName, workbook, reportGenerator);
                }
            }
        }
    }

    /**
     * Logic to handle the Precondition column dependency recursively.
     * Modified to preserve core configuration loops while safely isolating background dependencies.
     */
    private static void runSheetWithPrecondition(String sheetName, Workbook workbook, ReportGenerator reportGenerator) throws Exception {
        // If it's a structural background dependency sheet that ran already, do not re-run it
        if (executedSheets.contains(sheetName)) return;

        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) {
            System.err.println("⚠️ Warning: Sheet '" + sheetName + "' not found!");
            return;
        }

        // --- UPDATED MULTI-PRECONDITION SEARCH ---
        Row firstRow = sheet.getRow(1);
        if (firstRow != null) {
            Cell preconditionCell = firstRow.getCell(5);
            if (preconditionCell != null) {
                String fullText = preconditionCell.getStringCellValue();

                if (fullText.contains("RunSheet:")) {
                    // 1. Split by "RunSheet:" in case there are multiple mentions
                    String[] parts = fullText.split("RunSheet:");

                    // Skip index 0 as it is the text before the first "RunSheet:"
                    for (int j = 1; j < parts.length; j++) {
                        // 2. Clean the part to get the sheet names (handles comma-separated)
                        String rawNames = parts[j].split("\\n|\\r")[0].trim(); // Get the line
                        String[] dependencies = rawNames.split(","); // Split by comma

                        for (String dep : dependencies) {
                            String dependencySheet = dep.trim().split("\\s+")[0].replace(".", "");

                            if (!dependencySheet.isEmpty() && !executedSheets.contains(dependencySheet)) {
                                System.out.println("🔗 Multi-Dependency Found: [" + dependencySheet + "]");
                                runSheetWithPrecondition(dependencySheet, workbook, reportGenerator);
                            }
                        }
                    }
                }
            }
        }

        processSheetData(sheet, sheetName, reportGenerator);

        // Add to tracking list to prevent background dependency duplication loops
        executedSheets.add(sheetName);
    }

    /**
     * The actual loop that runs the test cases in the sheet
     */
    private static void processSheetData(Sheet sheet, String sheetName, ReportGenerator reportGenerator) {
        System.out.println("\n📖 Processing Sheet: [" + sheetName + "]");

        if (executor != null) {
            executor.setCleanupMode(sheetName.equalsIgnoreCase("DataCleanUpSheet"));
        }

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            Cell testCaseCell = row.getCell(2);
            Cell stepBlockCell = row.getCell(4);
            if (testCaseCell == null || stepBlockCell == null) continue;

            String testCaseName = testCaseCell.getStringCellValue().trim();
            String stepBlock = stepBlockCell.getStringCellValue().trim();

            String filterName = config.getProperty("filter.name");
            if (filterName != null && !testCaseName.toLowerCase().contains(filterName.toLowerCase().trim())) {
                continue;
            }

            String videoFileName = testCaseName.replaceAll("[^a-zA-Z0-9]", "_") + ".mp4";

            try {
                System.out.println("🎥 Starting Video Recording: " + videoFileName);
                videoRecorder.startRecording(reportGenerator.getReportDir(), videoFileName);

                if (!isBrowserStarted) {
                    executor.getDriver().get(config.getProperty("base.url"));
                    isBrowserStarted = true;
                } else {
                    String dashboardUrl = config.getProperty("dashboard.url");
                    if (dashboardUrl != null && !dashboardUrl.isEmpty()) {
                        executor.getDriver().get(dashboardUrl);
                    }
                    try { Thread.sleep(1500); } catch (Exception ignored) {}
                }

                executeTestCase(sheetName, testCaseName, stepBlock, executor, reportGenerator);

            } catch (Exception e) {
                System.err.println("❌ Error in " + testCaseName + ": " + e.getMessage());
            } finally {
                try {
                    videoRecorder.stopRecording();
                    reportGenerator.addVideoToTestCase(videoFileName);
                } catch (Exception ignored) {}
            }
        }
    }



    /**
     * Read test cases from CSV file
     */
    private static void readCSVTestCases(String csvPath, TestExecutor executor, ReportGenerator reportGenerator)
            throws Exception {
        List<CSVTestCaseReader.TestCaseData> testCases = CSVTestCaseReader.readTestCases(csvPath);

        System.out.println("✓ Found " + testCases.size() + " test case(s) in CSV file");

        for (CSVTestCaseReader.TestCaseData testCase : testCases) {
            String testCaseName = testCase.getTestCaseName();
            String stepBlock = testCase.getStepBlock();

            // Check for filter
            String filterName = config.getProperty("filter.name");
            boolean match = false;
            if (filterName != null && !filterName.isEmpty()) {
                String[] filters = filterName.split(",");
                for (String f : filters) {
                    if (testCaseName.toLowerCase().contains(f.trim().toLowerCase())) {
                        match = true;
                        break;
                    }
                }
                if (!match) {
                    continue; // Skip if no match found
                }
            }

            executeTestCase("CSV_Data",testCaseName, stepBlock, executor, reportGenerator);
        }
    }

    /**
     * Execute a single test case
     */
    private static void executeTestCase(String sheetName, String testCaseName, String stepBlock, TestExecutor executor,
                                        ReportGenerator reportGenerator) {
        System.out.println("\n=== 🧪 Running: " + testCaseName + " ===");

        // Parse steps
        List<TestStep> steps = StepParser.parseSteps(stepBlock);

        if (steps.isEmpty()) {
            System.err.println("❌ No valid steps parsed!");
            reportGenerator.startTestCase(testCaseName);
            reportGenerator.endTestCase(false);
            return;
        }

        // Execute test
        executor.run(sheetName, steps, testCaseName);
    }

    /**
     * Perform initial login before test execution
     */
    private static void performInitialLogin(TestExecutor executor) {
        System.out.println("\n╔════════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                        PERFORMING INITIAL LOGIN                                ║");
        System.out.println("╚════════════════════════════════════════════════════════════════════════════════╝");

        try {
            String url = config.getProperty("base.url");
            String username = config.getProperty("admin.email");
            String password = config.getProperty("admin.password");

            System.out.println("→ Navigating to login page: " + url);
            System.out.println("→ Logging in as: " + username);

            if (url != null && !url.isEmpty()) {
                executor.getDriver().get(url);
                LoginPage loginPage = new LoginPage(executor.getDriver(), executor.getWait());
                loginPage.login(username, password);
                System.out.println("✓ Login credentials submitted");
            }
        } catch (Exception e) {
            System.err.println("❌ Initial login failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}