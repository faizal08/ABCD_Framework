package com.eit.automation.core;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.eit.automation.actions.ClickActions;
import com.eit.automation.actions.FileActions;
import com.eit.automation.actions.InputActions;
import com.eit.automation.actions.ToastActions;
import com.eit.automation.actions.ScrollActions;
import com.eit.automation.actions.AutoItActions;
import com.eit.automation.actions.VerificationActions;
import com.eit.automation.actions.WaitActions;
import com.eit.automation.parser.TestStep;
import com.eit.automation.utils.ReportGenerator;

public class TestExecutor {

	private WebDriver driver;
	private WebDriverWait wait;

	// Action handlers
	private WaitActions waitActions;
	private ClickActions clickActions;
	private InputActions inputActions;
	private VerificationActions verificationActions;
	private FileActions fileActions;
	private ToastActions toastActions;
	private ScrollActions scrollActions;
	private AutoItActions autoItActions;

	private ActionRegistry actionRegistry; // NEW
	private PageObjectManager pageObjectManager; // NEW

	private ReportGenerator reportGenerator;

	// Logging configuration
	private boolean detailedLogging = true; // Enabled by default for better debugging
	private DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

	// Error tracking
	private int totalStepsExecuted = 0;
	private int passedSteps = 0;
	private int failedSteps = 0;

	public TestExecutor() {
		log("");
		log("╔════════════════════════════════════════════════════════════════════════════════╗");
		log("║                        INITIALIZING TEST EXECUTOR                              ║");
		log("╚════════════════════════════════════════════════════════════════════════════════╝");

		ChromeOptions options = new ChromeOptions();
		options.addArguments("--start-maximized");
		options.addArguments("--disable-notifications");
		options.setExperimentalOption("detach", true); // Keep browser open

		log("→ Creating ChromeDriver");
		log("  • Start maximized: Yes");
		log("  • Disable notifications: Yes");
		this.driver = new ChromeDriver(options);

		this.wait = new WebDriverWait(driver, Duration.ofSeconds(30));
		log("  • Default wait timeout: 30 seconds");

		log("→ Initializing action handlers");
		this.waitActions = new WaitActions(driver, wait);
		this.clickActions = new ClickActions(driver, wait, waitActions);
		this.inputActions = new InputActions(driver, wait, waitActions);
		this.verificationActions = new VerificationActions(driver, wait, waitActions);
		this.fileActions = new FileActions(driver, wait, waitActions);
		this.toastActions = new ToastActions(driver, wait, waitActions);
		this.scrollActions = new ScrollActions(driver, wait, waitActions);
		this.autoItActions = new AutoItActions(driver, wait, waitActions);
		log("✓ All action handlers ready");
		log("");
	}

	public TestExecutor(ReportGenerator reportGenerator) {
		this();
		this.reportGenerator = reportGenerator;
		log("✓ Report generator configured");
		log("");
	}

	/**
	 * Execute list of test steps - CONTINUES ON ERROR, DOESN'T CLOSE BROWSER
	 */
	public boolean run(List<TestStep> steps, String testCaseName) {
		long testStartTime = System.currentTimeMillis();

		// Reset counters
		totalStepsExecuted = 0;
		passedSteps = 0;
		failedSteps = 0;

		log("");
		log("╔════════════════════════════════════════════════════════════════════════════════╗");
		log("║  TEST CASE: " + padRight(testCaseName, 66) + "║");
		log("║  Total Steps: " + padRight(String.valueOf(steps.size()), 63) + "║");
		log("║  Start Time: " + padRight(getCurrentTime(), 64) + "║");
		log("╚════════════════════════════════════════════════════════════════════════════════╝");
		log("");

		try {
			if (reportGenerator != null) {
				reportGenerator.startTestCase(testCaseName);
			}

			// Execute all steps - CONTINUE EVEN ON ERROR
			for (int i = 0; i < steps.size(); i++) {
				TestStep step = steps.get(i);
				int stepNumber = i + 1;

				logStepHeader(stepNumber, steps.size(), step);

				long stepStartTime = System.currentTimeMillis();
				boolean stepPassed = false;

				try {

					executeStep(step);
					stepPassed = true;
					passedSteps++;

					long stepDuration = System.currentTimeMillis() - stepStartTime;
					logStepSuccess(stepNumber, stepDuration);

					if (reportGenerator != null) {
						reportGenerator.logStep(stepNumber, step, "PASSED", "", driver);
					}

				} catch (Exception e) {
					failedSteps++;

					long stepDuration = System.currentTimeMillis() - stepStartTime;
					logStepFailure(stepNumber, stepDuration, e);

					if (reportGenerator != null) {
						// Create detailed error message for HTML report
						// Create detailed error message for HTML report
						StringBuilder errorDetails = new StringBuilder();
						errorDetails.append("❌ MUST FIX: ")
								.append(e.getMessage() != null ? e.getMessage() : "Unknown Error").append("\n");

						if (e.getCause() != null) {
							String causeMsg = e.getCause().getMessage();
							// Clean up verbose Selenium info
							if (causeMsg != null) {
								int buildInfoIndex = causeMsg.indexOf("Build info:");
								if (buildInfoIndex > 0) {
									causeMsg = causeMsg.substring(0, buildInfoIndex).trim();
								}
								errorDetails.append("ℹ️ CAUSE: ").append(causeMsg).append("\n");
							}
						}
						errorDetails.append("⚠️ TYPE: ").append(e.getClass().getSimpleName());

						reportGenerator.logStep(stepNumber, step, "FAILED", errorDetails.toString(), driver);
					}

					// BREAK ON ERROR - FAIL FAST
					log("❌ Aborting current test case due to failure...");
					log("");
					break;
				}

				totalStepsExecuted++;
			}

			if (reportGenerator != null) {
				reportGenerator.endTestCase(failedSteps == 0);
			}

			long testDuration = System.currentTimeMillis() - testStartTime;
			logTestSummary(testCaseName, testDuration);

			// Return true only if ALL steps passed
			return failedSteps == 0;

		} catch (Exception e) {
			long testDuration = System.currentTimeMillis() - testStartTime;
			logCriticalFailure(testCaseName, testDuration, e);

			if (reportGenerator != null) {
				reportGenerator.endTestCase(false);
			}

			return false;
		}
	}

	/**
	 * Execute list of test steps WITHOUT test case name
	 */
	public boolean run(List<TestStep> steps) {
		return run(steps, "Unnamed Test Case");
	}

	/**
	 * Execute single test step - NEVER THROWS, JUST LOGS ERRORS
	 */
	private void executeStep(TestStep step) throws Exception {
		String action = step.getAction().toLowerCase();
		String value = step.getValue();
		String xpath = step.getXpath();
		String context = step.getContext();

		log("  ⚙ Action: " + action.toUpperCase());

		/*// HOTFIX: Override file upload path with user provided path
		if ((action.equals("uploadfile") || action.equals("robotupload")) && value != null) {
			log("  ⚠ HOTFIX: Overriding file path with 'C:\\Vehicle Image\\Auto.jpg'");
			value = "C:\\Vehicle Image\\Auto.jpg";
		}*/

		// 1. PAGEFACTORY LOOKUP (If XPath is empty, try to match value/context to a
		// Page Object field)
		if ((xpath == null || xpath.isEmpty()) && (value != null && !value.isEmpty())) {
			if (pageObjectManager != null) {

				WebElement element = pageObjectManager.findElementByName(value);
				if (element != null) {
					log("  → Found PageFactory Element: " + value);
				}
			}
		}

		// Auto-generate XPath if empty (Legacy fallback)
		if (xpath == null || xpath.isEmpty()) {
			if (value != null && !value.isEmpty()) {
				if (!action.startsWith("verifytoast") && !action.equals("verifysuccesstoast")
						&& !action.equals("verifyerrortoast") && !action.equals("verifyalert")
						&& !action.equals("verifyalertmessage") && !action.equals("verifynotification")
						&& !action.equals("robotupload")) {

					xpath = generateXPathFromValue(value, context);
					log("  → Auto-generated XPath: " + xpath);
				} else {
					log("  → Using auto-detection for toast/alert");
				}
			}
		}

		if ((xpath == null || xpath.isEmpty()) && (value == null || value.isEmpty())) {
			log("  ⚠ Both XPath and Value empty - skipping");
			// throw new RuntimeException("Both XPath and Value cannot be empty for action:
			// " + action);
		}

		switch (action) {
			case "openurl":
			case "navigate":
				log("  → URL: " + value);
				driver.get(value);
				waitActions.waitForPageLoad();
				log("  ✓ Page loaded: " + driver.getCurrentUrl());
				break;
			// ... (Removing migrated cases to clean up? Or keeping as fallback?
			// For safety, I'll keep the switch case logic for now, but the Registry check
			// above protects us.)
			case "scrolltobottom":
				log("  → Scrolling to bottom");
				scrollActions.scrollToBottom();
				log("  ✓ Scrolled to bottom");
				break;

			case "scrolltotop":
				log("  → Scrolling to top");
				scrollActions.scrollToTop();
				log("  ✓ Scrolled to top");
				break;

			case "scrolltoelement":
				log("  → XPath: " + xpath);
				WebElement elementToScroll = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(xpath)));
				scrollActions.scrollToElement(elementToScroll);
				log("  ✓ Scrolled to element");
				break;

			case "scrollby":
				log("  → Scroll Amount: " + value);
				String[] coords = value.split(",");
				if (coords.length >= 2) {
					int x = Integer.parseInt(coords[0].trim());
					int y = Integer.parseInt(coords[1].trim());
					scrollActions.scrollBy(x, y);
					log("  ✓ Scrolled by " + x + ", " + y);
				} else {
					log("  ⚠ Invalid scroll amount format (expected 'x,y'): " + value);
				}
				break;

			case "click":
				// case "select":
				log("  → XPath: " + xpath);
				clickActions.clickElementWithRetry(xpath, value);
				log("  ✓ Clicked");
				break;
			case "select":
				log("  → XPath: " + xpath);
				clickActions.selectElementWithRetry(xpath, value);
				log("  ✓ Selected");
				break;
			case "type":
			case "enter":
				log("  → XPath: " + xpath);
				if (xpath != null && (xpath.toLowerCase().contains("password") || xpath.toLowerCase().contains("pwd")
						|| xpath.toLowerCase().contains("pass"))) {
					log("  → Value: ********** (hidden)");
				} else {
					log("  → Value: "
							+ (value != null && value.length() > 60 ? value.substring(0, 60) + "..." : value));
				}
				inputActions.typeText(xpath, value);
				log("  ✓ Text entered");
				break;

			case "clear":
				log("  → XPath: " + xpath);
				inputActions.clearField(xpath);
				log("  ✓ Field cleared");
				break;

			case "uploadfile":
			case "selectfile":
			case "attachfile":
				log("  → File: " + value);
				log("  → XPath: " + xpath);
				fileActions.uploadFile(value, xpath);
				log("  ✓ File uploaded");
				break;

			case "robotupload":
				log("  → File: " + value);
				// XPath is not strictly needed for Robot paste, but we might want to Click
				// first?
				// Usually Robot upload implies we already clicked the button.
				// But for consistency, let's assume the previous step clicked the button.
				// Or if XPath is provided, we click it first.
				if (xpath != null && !xpath.isEmpty()) {
					log("  → Clicking upload button first: " + xpath);
					driver.findElement(By.xpath(xpath)).click(); // Simple click
					waitActions.waitFor(1000); // Wait for dialog
				}
				fileActions.uploadFileWithRobot(value);
				log("  ✓ File uploaded via Robot");
				break;

			case "waitforupload":
				log("  → Waiting for upload completion");
				log("  → XPath: " + xpath);
				fileActions.waitForUploadComplete(xpath);
				log("  ✓ Upload complete");
				break;

			case "verifytoast":
			case "verifytoastmessage":
			case "verifysuccesstoast":
			case "verifyerrortoast":
				log("  → Expected: " + value);
				if (xpath != null && !xpath.isEmpty()) {
					log("  → XPath: " + xpath);
					toastActions.verifyToastMessage(value, xpath);
				} else {
					log("  → Auto-detecting toast");
					toastActions.verifyToastMessageByText(value);
				}
				log("  ✓ Toast verified");
				break;

			// case "verifysuccesstoast":
			// log(" → Expected: " + value);
			// toastActions.verifySuccessToast(value);
			// log(" ✓ Success toast verified");
			// break;

			// case "verifyerrortoast":
			// log(" → Expected: " + value);
			// toastActions.verifyErrorToast(value);
			// log(" ✓ Error toast verified");
			// break;

			case "verifyalert":
			case "verifyalertmessage":
				log("  → Expected: " + (value != null && !value.isEmpty() ? value : "(just verify presence)"));
				log("  → XPath: " + xpath);
				toastActions.verifyToastMessage(value, xpath);
				log("  ✓ Alert verified");
				break;

			case "waitfortoast":
				log("  → XPath: " + xpath);
				toastActions.waitForToastToAppearAndDisappear(xpath);
				log("  ✓ Toast lifecycle complete");
				break;

			case "verify":
			case "verifyvisible":
			case "verifydisplayed":
				log("  → XPath: " + xpath);
				verificationActions.verifyElementVisible(xpath);
				log("  ✓ Element visible");
				break;

			case "verifytext":
				log("  → XPath: " + xpath);
				log("  → Expected: " + value);
				verificationActions.verifyElementValueOrText(xpath, value);
				log("  ✓ Text verified");
				break;

			case "verifyvalue":
				log("  → XPath: " + xpath);
				log("  → Expected: " + value);
				verificationActions.verifyElementValue(xpath, value);
				log("  ✓ Value verified");
				break;

			case "drawpolygon":
				log("  → Canvas XPath: " + xpath);
				log("  → Points: " + value);
				drawPolygon(xpath, value);
				log("  ✓ Polygon drawn");
				break;

			case "verifydate":
				log("  → XPath: " + xpath);
				if (value != null && !value.isEmpty()) {
					log("  → Expected Date: " + value);
					verificationActions.verifyElementDate(xpath, value);
				} else {
					verificationActions.verifyDateFieldHasValue(xpath);
				}
				log("  ✓ Date verified");
				break;

			case "verifycurrentdate":
			case "verifytodaydate":
				log("  → XPath: " + xpath);
				verificationActions.verifyDateFieldIsToday(xpath);
				log("  ✓ Date is today");
				break;

			case "verifyenabled":
				log("  → XPath: " + xpath);
				verificationActions.verifyElementEnabled(xpath);
				log("  ✓ Element enabled");
				break;

			case "verifydisabled":
				log("  → XPath: " + xpath);
				verificationActions.verifyElementDisabled(xpath);
				log("  ✓ Element disabled");
				break;

			case "verifyselected":
			case "verifychecked":
				log("  → XPath: " + xpath);
				verificationActions.verifyElementSelected(xpath);
				log("  ✓ Element selected");
				break;

			case "verifyexists":
			case "verifypresent":
				log("  → XPath: " + xpath);
				verificationActions.verifyElementExists(xpath);
				log("  ✓ Element exists");
				break;

			case "verifyhidden":
			case "verifynotvisible":
				log("  → XPath: " + xpath);
				verificationActions.verifyElementNotVisible(xpath);
				log("  ✓ Element hidden");
				break;

			case "verifycontains":
				log("  → XPath: " + xpath);
				log("  → Expected to contain: " + value);
				verificationActions.verifyElementContainsText(xpath, value);
				log("  ✓ Text contains expected");
				break;

			case "verifycount":
				int count = Integer.parseInt(value);
				log("  → XPath: " + xpath);
				log("  → Expected count: " + count);
				verificationActions.verifyElementCount(xpath, count);
				log("  ✓ Count verified");
				break;

			case "verifyattribute":
				String[] parts = value.split("=", 2);
				log("  → XPath: " + xpath);
				log("  → Attribute: " + parts[0] + " = " + parts[1]);
				verificationActions.verifyElementAttribute(xpath, parts[0], parts[1]);
				log("  ✓ Attribute verified");
				break;

			case "verifypagetitle":
			case "verifytitle":
				log("  → Expected Title: " + value);
				verificationActions.verifyPageTitle(value);

				// Dual verification: If xpath is matched, verify that too
				if (xpath != null && !xpath.isEmpty()) {
					log("  → Also verifying element visibility: " + xpath);
					verificationActions.verifyElementVisible(xpath);
				}

				log("  ✓ Page title verified");
				break;

			case "verifypagetitlecontains":
			case "verifytitlecontains":
				log("  → Expected Title to contain: " + value);
				verificationActions.verifyPageTitleContains(value);
				log("  ✓ Page title verified");
				break;

			case "verifyurl":
			case "verifycurrenturl":
				log("  → Expected URL: " + value);
				verificationActions.verifyCurrentUrl(value);
				log("  ✓ URL verified");
				break;

			case "verifyurlcontains":
				log("  → Expected URL to contain: " + value);
				verificationActions.verifyUrlContains(value);
				log("  ✓ URL verified");
				break;

			case "verifymapshape":
			case "verifypolygon":
			case "verifymapelement":
				log("  → Map XPath: " + xpath);
				verificationActions.verifyMapShapePresent(xpath);
				log("  ✓ Map shape verified");
				break;

			case "verifygridvalue":
				log("  → Grid XPath: " + xpath);
				String[] gridParts = value.split("=", 2);
				if (gridParts.length < 2) {
					throw new RuntimeException(
							"Invalid format for verifygridvalue. Expected 'ColumnName=ExpectedValue', got: " + value);
				}
				String colName = gridParts[0].trim();
				String expectedVal = gridParts[1].trim();

				log("  → Column: " + colName);
				log("  → Expected Value: " + expectedVal);

				verificationActions.verifyGridCellValue(xpath, colName, expectedVal);
				log("  ✓ Grid value verified");
				break;

			case "autoit":
			case "executeautoit":
			case "runautoit":
				log("  → Script Path: " + value);
				// Arguments can be in XPath (if starts with //) or Context (if just string)
				String scriptArgs = "";
				if (context != null && !context.isEmpty()) {
					scriptArgs = context;
				} else if (xpath != null && !xpath.isEmpty()) {
					scriptArgs = xpath;
				}

				if (!scriptArgs.isEmpty()) {
					log("  → Arguments: " + scriptArgs);
				}
				autoItActions.executeScript(value, scriptArgs);
				log("  ✓ AutoIT script executed");
				break;

			case "wait":
				if (value != null && value.matches("\\d+")) {
					waitActions.waitFor(Long.parseLong(value));
					log("  ✓ Waited " + value + "ms");
				} else if (xpath != null && !xpath.isEmpty()) {
					waitActions.waitForElementVisible(xpath);
					log("  ✓ Waited for element visible (fallback)");
				} else {
					log("  ⚠ Wait action with no value or xpath - default 1s wait");
					waitActions.waitFor(1000);
				}
				break;

			case "waitforvisible":
			case "wait for visible":
				log("  → XPath: " + xpath);
				waitActions.waitForElementVisible(xpath);
				log("  ✓ Element is visible");
				break;

			case "waitforclickable":
			case "wait for clickable":
				log("  → XPath: " + xpath);
				waitActions.waitForElementClickable(xpath);
				log("  ✓ Element is clickable");
				break;

			default:
				throw new RuntimeException("Unknown action: " + action);
		}
	}

	private String generateXPathFromValue(String value, String context) {
		if (context != null && !context.isEmpty()) {
			return String.format(
					"//tr[contains(., '%2$s')]//*[contains(text(), '%1$s') or @title='%1$s' or @alt='%1$s' or @aria-label='%1$s' or contains(@class, '%1$s')]",
					value, context);
		}

		return String.format(
				"//*[normalize-space()='%1$s' or @placeholder='%1$s' or @value='%1$s' or @title='%1$s' or @name='%1$s' or @id='%1$s' or @aria-label='%1$s' or @data-testid='%1$s' or contains(text(), '%1$s')]",
				value);
	}

	private void drawPolygon(String xpath, String value) {
		WebElement map = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(xpath)));

		((org.openqa.selenium.JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", map);

		int width = map.getSize().getWidth();
		int height = map.getSize().getHeight();
		int maxX = (width / 2) - 20;
		int maxY = (height / 2) - 20;

		Actions actions = new Actions(driver);
		String[] points = value.split("\\s*:\\s*");

		int[] first = null;
		for (int i = 0; i < points.length; i++) {
			int[] xy = parsePoint(points[i], maxX, maxY);
			if (i == 0) first = xy;

			actions.moveToElement(map, xy[0], xy[1])
					.click()
					.pause(Duration.ofMillis(700))
					.perform();
		}

		if (first != null) {
			// MOVE back to start
			actions.moveToElement(map, first[0], first[1]).perform();

			// TRIPLE CLICK to force the 'drawend' event in OpenLayers
			actions.click().pause(Duration.ofMillis(100))
					.click().pause(Duration.ofMillis(100))
					.click().pause(Duration.ofMillis(200))
					.perform();

			// TAB and ENTER: Forces the application to 'Notice' the data change
			actions.sendKeys(org.openqa.selenium.Keys.TAB).pause(Duration.ofMillis(200))
					.sendKeys(org.openqa.selenium.Keys.ENTER).perform();
		}

		log("  ✓ Polygon committed. Sending TAB to update form state.");
	}
	private int[] parsePoint(String point, int maxX, int maxY) {
		String[] xy = point.split(";");
		if (xy.length < 2) {
			throw new RuntimeException("Invalid point format: '" + point + "'. Expected 'X;Y'");
		}
		int x = Integer.parseInt(xy[0].trim());
		int y = Integer.parseInt(xy[1].trim());
		x = Math.max(-maxX, Math.min(maxX, x));
		y = Math.max(-maxY, Math.min(maxY, y));
		return new int[] { x, y };
	}

	public void close() {
		log("");
		log("╔════════════════════════════════════════════════════════════════════════════════╗");
		log("║                           CLOSING BROWSER                                      ║");
		log("╚════════════════════════════════════════════════════════════════════════════════╝");
		if (driver != null) {
			driver.quit();
			log("✓ Browser closed");
		}
		log("");
	}

	public WebDriver getDriver() {
		return driver;
	}

	// ========================================
	// LOGGING METHODS - CLEAN & READABLE
	// ========================================

	private void log(String message) {
		if (detailedLogging) {
			System.out.println("[" + getCurrentTime() + "] " + message);
		}
	}

	private void logStepHeader(int stepNumber, int totalSteps, TestStep step) {
		log("");
		log("┌────────────────────────────────────────────────────────────────────────────────┐");
		log("│ STEP " + stepNumber + "/" + totalSteps + " │ " + step.getAction().toUpperCase()
				+ " ".repeat(Math.max(1, 68 - step.getAction().length() - String.valueOf(stepNumber).length()
						- String.valueOf(totalSteps).length()))
				+ "│");
		log("├────────────────────────────────────────────────────────────────────────────────┤");

		String value = step.getValue() != null ? step.getValue() : "";
		if (value.length() > 70)
			value = value.substring(0, 67) + "...";
		if (!value.isEmpty()) {
			log("│ Value: " + value + " ".repeat(Math.max(1, 73 - value.length())) + "│");
		}

		String xpath = step.getXpath() != null ? step.getXpath() : "";
		if (xpath.length() > 70)
			xpath = xpath.substring(0, 67) + "...";
		if (!xpath.isEmpty()) {
			log("│ XPath: " + xpath + " ".repeat(Math.max(1, 73 - xpath.length())) + "│");
		}

		log("└────────────────────────────────────────────────────────────────────────────────┘");
	}

	private void logStepSuccess(int stepNumber, long duration) {
		log("");
		log("  ✅ STEP " + stepNumber + " PASSED [" + duration + "ms]");
		log("");
	}

	private void logStepFailure(int stepNumber, long duration, Exception e) {
		log("");
		System.err.println("  ❌ STEP " + stepNumber + " FAILED [" + duration + "ms]");
		System.err.println("  ┌─ Error Details ─────────────────────────────────────────────────────────");
		System.err.println("  │ Must Fix: " + (e.getMessage() != null ? e.getMessage() : "Unknown Error"));
		if (e.getCause() != null) {
			System.err.println("  │ Cause: " + e.getCause().getMessage());
		}
		System.err.println("  └─────────────────────────────────────────────────────────────────────────");
		log("");
	}

	private void logTestSummary(String testName, long duration) {
		log("");
		log("╔════════════════════════════════════════════════════════════════════════════════╗");
		log("║                           TEST CASE SUMMARY                                    ║");
		log("╠════════════════════════════════════════════════════════════════════════════════╣");
		log("║  Test Case: " + padRight(testName, 66) + "║");
		log("║  Total Steps: " + padRight(String.valueOf(totalStepsExecuted), 63) + "║");
		log("║  Passed: " + padRight(String.valueOf(passedSteps), 68) + "║");
		log("║  Failed: " + padRight(String.valueOf(failedSteps), 68) + "║");
		log("║  Duration: " + padRight(formatDuration(duration), 66) + "║");
		log("║  End Time: " + padRight(getCurrentTime(), 66) + "║");
		log("╠════════════════════════════════════════════════════════════════════════════════╣");
		if (failedSteps == 0) {
			log("║  STATUS: ✓ ALL STEPS PASSED                                                   ║");
		} else {
			log("║  STATUS: ✗ " + failedSteps + " STEP(S) FAILED                                            ║");
		}
		log("╚════════════════════════════════════════════════════════════════════════════════╝");
		log("");
		log("→ Browser remains open for inspection");
		log("→ Call executor.close() when done");
		log("");
	}

	private void logCriticalFailure(String testName, long duration, Exception e) {
		log("");
		log("╔════════════════════════════════════════════════════════════════════════════════╗");
		log("║                         CRITICAL TEST FAILURE                                  ║");
		log("╠════════════════════════════════════════════════════════════════════════════════╣");
		log("║  Test Case: " + padRight(testName, 66) + "║");
		log("║  Duration: " + padRight(formatDuration(duration), 67) + "║");
		log("║  Error: " + padRight(e.getClass().getSimpleName(), 70) + "║");
		log("╚════════════════════════════════════════════════════════════════════════════════╝");
		System.err.println("Full Stack Trace:");
		e.printStackTrace();
	}

	private String getCurrentTime() {
		return LocalDateTime.now().format(timeFormatter);
	}

	private String formatDuration(long milliseconds) {
		long seconds = milliseconds / 1000;
		long ms = milliseconds % 1000;
		if (seconds > 60) {
			long minutes = seconds / 60;
			seconds = seconds % 60;
			return String.format("%dm %ds %dms", minutes, seconds, ms);
		} else {
			return String.format("%ds %dms", seconds, ms);
		}
	}

	public WebDriverWait getWait() {
		return wait;
	}

	public void setWait(WebDriverWait wait) {
		this.wait = wait;
	}

	public void setDriver(WebDriver driver) {
		this.driver = driver;
	}

	private String padRight(String s, int n) {
		return String.format("%-" + n + "s", s);
	}

	public void setDetailedLogging(boolean enabled) {
		this.detailedLogging = enabled;
	}

	public int getTotalStepsExecuted() {
		return totalStepsExecuted;
	}

	public int getPassedSteps() {
		return passedSteps;
	}

	public int getFailedSteps() {
		return failedSteps;
	}
}