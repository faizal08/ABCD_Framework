package com.eit.automation.core;

import com.eit.automation.pages.LoginPage;
import com.eit.automation.parser.StepParser;
import com.eit.automation.parser.TestStep;
import com.eit.automation.utils.CSVTestCaseReader;
import com.eit.automation.utils.ReportGenerator;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;
import java.util.Properties;

public class Main {
    public static Properties config;
    public static TestExecutor executor;

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
        String sheetName = config.getProperty("sheets.name");
        String[] sheetNames = (sheetName != null) ? sheetName.split(",") : new String[0];

        try (FileInputStream fis = new FileInputStream(excelPath);
             Workbook workbook = new XSSFWorkbook(fis)) {

            if (executor == null) {
                executor = new TestExecutor(reportGenerator);
            }

            boolean isBrowserStarted = false;

            for (String rawSheetName : sheetNames) {
                String sheetSingleName = rawSheetName.trim();
                Sheet sheet = workbook.getSheet(sheetSingleName);
                if (sheet == null) {
                    System.err.println("⚠️ Warning: Sheet '" + sheetSingleName + "' not found!");
                    continue;
                }

                for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) continue;

                    Cell testCaseCell = row.getCell(2);
                    Cell stepBlockCell = row.getCell(4);
                    if (testCaseCell == null || stepBlockCell == null) continue;

                    String testCaseName = testCaseCell.getStringCellValue().trim();
                    String stepBlock = stepBlockCell.getStringCellValue().trim();

                    // Apply Filter Logic
                    String filterName = config.getProperty("filter.name");
                    if (filterName != null && !filterName.isEmpty()) {
                        if (!testCaseName.toLowerCase().contains(filterName.toLowerCase().trim())) {
                            continue;
                        }
                    }

                    // 1. NAVIGATION LOGIC
                    if (!isBrowserStarted) {
                        // Navigate to Login for the very first test case
                        executor.getDriver().get(config.getProperty("base.url"));
                        isBrowserStarted = true;
                    } else {
                        // Navigate to Dashboard for subsequent tests to clear the previous page
                        System.out.println("🔄 Navigating to Dashboard for: " + testCaseName);

                        String dashboardUrl = config.getProperty("dashboard.url");
                        if (dashboardUrl != null && !dashboardUrl.isEmpty()) {
                            executor.getDriver().get(dashboardUrl);                        } else {
                            System.err.println("❌ Error: 'dashboard.url' not found in config.properties!");
                        }

                        // Small wait to allow the Dashboard page to load naturally
                        try { Thread.sleep(1500); } catch (Exception ignored) {}
                    }

                    // Execute the test steps (Success messages and popups will NOT be deleted now)
                    executeTestCase(testCaseName, stepBlock, executor, reportGenerator);
                }
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

            executeTestCase(testCaseName, stepBlock, executor, reportGenerator);
        }
    }

    /**
     * Execute a single test case
     */
    private static void executeTestCase(String testCaseName, String stepBlock, TestExecutor executor,
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
        executor.run(steps, testCaseName);
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