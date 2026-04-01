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

            // Load configuration
            config = new Properties();
            FileInputStream configFis = new FileInputStream("config.properties");
            config.load(configFis);

            String excelName = config.getProperty("excel.name", "Default_Test.xlsx");
            reportGenerator.setExcelFileName(excelName);

            reportGenerator.startTestExecution();

            // Determine test file path
            String testFilePath = config.getProperty("excel.name");

            System.out.println("📂 Reading test cases from: " + testFilePath);
            File testFile = new File(testFilePath);
            if (!testFile.exists()) {
                throw new RuntimeException("Test file not found: " + testFile.getAbsolutePath());
            }

            // Detect file type and read accordingly
            if (testFilePath.toLowerCase().endsWith(".csv")) {
                System.out.println("📄 Detected CSV file format");
                // Initialize executor inside or pass it in - following your original style
                executor = new TestExecutor(reportGenerator);
                readCSVTestCases(testFilePath, executor, reportGenerator);
            } else if (testFilePath.toLowerCase().endsWith(".xlsx") || testFilePath.toLowerCase().endsWith(".xls")) {
                System.out.println("📊 Detected Excel file format");
                // We do not initialize here anymore because we need a fresh one per sheet
                readExcelTestCases(testFilePath, reportGenerator);
            } else {
                throw new RuntimeException("Unsupported file format. Please use .csv, .xlsx, or .xls files.");
            }

            reportGenerator.endTestExecution();

        } catch (Exception e) {
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
     * Read test cases from Excel file - UPDATED TO HANDLE MULTIPLE SHEETS CORRECTLY
     */
    private static void readExcelTestCases(String excelPath, ReportGenerator reportGenerator) throws Exception {
        String sheetNameConfig = config.getProperty("sheets.name");
        String[] sheetNames = (sheetNameConfig != null) ? sheetNameConfig.split(",") : new String[0];

        try (FileInputStream fis = new FileInputStream(excelPath);
             Workbook workbook = new XSSFWorkbook(fis)) {

            if (executor == null) {
                executor = new TestExecutor(reportGenerator);
            }

            for (String rawSheetName : sheetNames) {
                runSheetWithPrecondition(rawSheetName.trim(), workbook, reportGenerator);
            }
        }
    }

    /**
     * Logic to handle the Precondition column dependency recursively.
     * Handles long descriptive text by searching for the "RunSheet:" keyword.
     */
    private static void runSheetWithPrecondition(String sheetName, Workbook workbook, ReportGenerator reportGenerator) throws Exception {
        // 1. Prevent running the same sheet twice
        if (executedSheets.contains(sheetName)) return;

        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) {
            System.err.println("⚠️ Warning: Sheet '" + sheetName + "' not found!");
            return;
        }

        // --- SMART PRECONDITION SEARCH ---
        // Check the first row for dependencies
        Row firstRow = sheet.getRow(1);
        if (firstRow != null) {
            Cell preconditionCell = firstRow.getCell(5); // Column 5 (Precondition)
            if (preconditionCell != null) {
                String fullText = preconditionCell.getStringCellValue();

                // We use a simple check to see if the trigger keyword exists
                if (fullText.contains("RunSheet:")) {
                    // This logic splits by "RunSheet:", takes the part after it,
                    // and then takes the first word (the sheet name) ignoring any text after it.
                    String afterKeyword = fullText.split("RunSheet:")[1].trim();
                    String dependencySheet = afterKeyword.split("\\s+|\\n|\\r")[0].replace(".", "").trim();

                    if (!executedSheets.contains(dependencySheet)) {
                        System.out.println("🔗 Dependency Found in Description: [" + dependencySheet + "]");
                        System.out.println("🚀 Auto-triggering prerequisite sheet...");
                        runSheetWithPrecondition(dependencySheet, workbook, reportGenerator);
                    }
                }
            }
        }

        // 2. Now proceed to run the actual sheet test cases
        processSheetData(sheet, sheetName, reportGenerator);
        executedSheets.add(sheetName);
    }
    /**
     * The actual loop that runs the test cases in the sheet
     */
    private static void processSheetData(Sheet sheet, String sheetName, ReportGenerator reportGenerator) {
        System.out.println("\n📖 Processing Sheet: [" + sheetName + "]");

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