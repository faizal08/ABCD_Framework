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

            reportGenerator.startTestExecution();

            // Determine test file path
            String testFilePath = config.getProperty("excel.name");

            System.out.println("📂 Reading test cases from: " + testFilePath);
            File testFile = new File(testFilePath);
            if (!testFile.exists()) {
                throw new RuntimeException("Test file not found: " + testFile.getAbsolutePath());
            }

            // Initialize executor FIRST
            executor = new TestExecutor(reportGenerator);

            // Perform Login ONCE
            // performInitialLogin(executor);

            // Detect file type and read accordingly
            if (testFilePath.toLowerCase().endsWith(".csv")) {
                System.out.println("📄 Detected CSV file format");
                readCSVTestCases(testFilePath, executor, reportGenerator);
            } else if (testFilePath.toLowerCase().endsWith(".xlsx") || testFilePath.toLowerCase().endsWith(".xls")) {
                System.out.println("📊 Detected Excel file format");
                readExcelTestCases(testFilePath, executor, reportGenerator);
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
                // System.out.println("✨ Browser remains open for inspection.");
            }
        }
    }

    /**
     * Read test cases from Excel file
     */
    private static void readExcelTestCases(String excelPath, TestExecutor executor, ReportGenerator reportGenerator)
            throws Exception {
        String sheetName = config.getProperty("sheets.name");
        String[] sheetNames = null;
        if (sheetName != null && !sheetName.isEmpty()) {
            sheetNames = sheetName.split(",");
        } else {
            throw new RuntimeException("sheets.name property is required for Excel files");
        }

        try (FileInputStream fis = new FileInputStream(excelPath);
                Workbook workbook = new XSSFWorkbook(fis)) {

            for (String rawSheetName : sheetNames) {
                String sheetSingleName = rawSheetName.trim();
                // List all sheets for debugging
                System.out.println("📚 Workbook contains sheets: ");
                for (int k = 0; k < workbook.getNumberOfSheets(); k++) {
                    System.out.println("   - " + workbook.getSheetName(k));
                }

                Sheet sheet = workbook.getSheet(sheetSingleName);
                if (sheet == null) {
                    System.err.println("⚠ Warning: Sheet '" + sheetSingleName + "' not found in workbook!");
                    continue;
                }

                System.out.println("✅ Found Sheet: '" + sheetSingleName + "' with " + sheet.getLastRowNum() + " rows.");

                // Execute each test case
                for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                    System.out.println("   ➡ Checking Row " + i);
                    Row row = sheet.getRow(i);
                    if (row == null) {
                        System.out.println("   ⚠ Row " + i + " is null");
                        continue;
                    }

                    Cell testCaseCell = row.getCell(2); // Column C
                    Cell stepBlockCell = row.getCell(4); // Column E

                    if (testCaseCell == null || stepBlockCell == null)
                        continue;

                    String testCaseName = testCaseCell.getStringCellValue().trim();
                    String stepBlock = stepBlockCell.getStringCellValue().trim();

                    System.out.println("🔎 Found Test Case in Excel: " + testCaseName);

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

                    // Reset state before test case
                    System.out.println("🧹 Resetting browser state...");
                    executor.getDriver().manage().deleteAllCookies();
                    String baseUrl = config.getProperty("base.url");
                    if (baseUrl != null && !baseUrl.isEmpty()) {
                        executor.getDriver().get(baseUrl);
                    }

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
        // Log start of login process with a nice header
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
                // Navigate first
                executor.getDriver().get(url);

                // Then login
                LoginPage loginPage = new LoginPage(executor.getDriver(), executor.getWait());
                loginPage.login(username, password);

                System.out.println("✓ Login credentials submitted");
            }
        } catch (Exception e) {
            System.err.println("❌ Initial login failed: " + e.getMessage());
            e.printStackTrace();
            // We continue even if login fails, as some tests might not need it or might
            // handle it themselves
        }
    }
}