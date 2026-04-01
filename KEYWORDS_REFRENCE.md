# 📘 ABCD Automation Framework: Full Keywords Dictionary

This document is the official reference for writing automated test cases. The `StepParser` uses natural language processing (Regex) to match your Excel steps to automation actions.

---

## 🛠️ 1. Navigation & System

| Action | Phrase Examples (Natural Language) | Description |
| :--- | :--- | :--- |
| **openurl** | `Maps to`, `open url`, `go to url`, `Maps` | Opens a specific website URL. |
| **back / forward** | `back`, `forward` | Browser navigation history. |
| **max / min** | `maximize`, `minimize` | Controls the browser window size. |
| **screenshot** | `screenshot`, `take screenshot` | Captures the current screen. |

**Excel Example:**

| Test Step Description | Action | Value | Target (XPath) |
| :--- | :--- | :--- | :--- |
| Open Login Page | openurl | - | https://dev.we1.co/#/login |
| Maximize Browser | maximize | - | - |

---

## ⌨️ 2. Interactions & Input

| Action | Phrase Examples (Natural Language) | Description |
| :--- | :--- | :--- |
| **type** | `enter the`, `type`, `input`, `fill` | Enters text into a field. |
| **click** | `click`, `press` | Clicks a button, link, or element. |
| **clear** | `clear text`, `empty field`, `remove value` | Wipes the content of an input box. |
| **select** | `select [value]` | Selects from a dropdown (excludes file/radio). |
| **tab** | `tab key`, `tab` | Simulates the **TAB** key. |
| **press_enter** | `press enter`, `enter key` | Simulates the **ENTER** key. |
| **arrows** | `arrow_down`, `arrow_up` | Simulates keyboard arrow keys. |

**Excel Example:**

| Test Step Description | Action | Value | Target (XPath) |
| :--- | :--- | :--- | :--- |
| Enter SuperAdmin Email | type | admin@gmail.com | //input[@placeholder='Enter email'] |
| Click Login Button | click | - | //button[@type='submit'] |
| Scroll Grid Right | tab | 5 | //div[@class='grid-body'] |

---

## 📁 3. File Uploads & System Tools

| Action | Phrase Examples (Natural Language) | Description |
| :--- | :--- | :--- |
| **uploadfile** | `upload`, `attach`, `select file`, `choose file` | Handles standard web file uploads. |
| **waitforupload**| `wait for upload` | Pauses until a file finishes uploading. |
| **autoit** | `autoit`, `runautoit`, `executeautoit` | Triggers an AutoIt script for system dialogs. |
| **robotupload** | `robot`, `robotupload`, `uploadrobot` | Uses Java Robot class for OS-level uploads. |

**Excel Example:**

| Test Step Description | Action | Value | Target (XPath) |
| :--- | :--- | :--- | :--- |
| Upload License Image | uploadfile | src/main/resources/test-data/license.jpg | //input[@type='file'] |

---

## 🔍 4. Verification & Assertions

| Action | Phrase Examples (Natural Language) | Description |
| :--- | :--- | :--- |
| **verifyvisible** | `displayed`, `visible`, `appear`, `shown` | Confirms element is on screen. |
| **verifyhidden** | `not displayed`, `should not appear`, `hidden` | Confirms element is NOT visible. |
| **verifytext** | `verify text`, `label`, `verify exact text` | Checks if the text matches exactly. |
| **verifycontains**| `contains`, `text contains`, `label contains` | Checks if text exists within a string. |
| **verifyenabled** | `enabled`, `disabled` | Checks if a button is clickable or greyed out. |
| **verifyselected**| `selected`, `checked` | Checks state of checkboxes/radio buttons. |

**Excel Example:**

| Test Step Description | Action | Value | Target (XPath) |
| :--- | :--- | :--- | :--- |
| Verify Dashboard Load | verifyvisible | - | //div[contains(text(),'Statistics')] |
| Check Success Toast | verifytext | Added Successfully | //div[@role='alert'] |

---

## ⏳ 5. Explicit Waits & Toasts

| Action | Phrase Examples (Natural Language) | Description |
| :--- | :--- | :--- |
| **waitforvisible**| `wait until visible`, `wait for visible` | Pauses until element appears. |
| **waitforpageload**| `wait for page`, `wait for load` | Waits for the entire page to finish loading. |
| **waitfortoast** | `wait for toast` | Pauses for the success/error message popup. |
| **verifysuccesstoast**| `toast success` | Specifically checks for a green success toast. |
| **wait** | `wait 5` | A simple static pause (value is in seconds). |

**Excel Example:**

| Test Step Description | Action | Value | Target (XPath) |
| :--- | :--- | :--- | :--- |
| Wait for Success | waitfortoast | - | - |
| Hard Pause | wait | 3 | - |

---

## 🖱️ 6. Scrolling, Frames & Maps

| Action | Phrase Examples (Natural Language) | Description |
| :--- | :--- | :--- |
| **scrolltoelement**| `scroll to element`, `scroll to` | Moves view to a specific element. |
| **scrolltotop** | `scroll up`, `scroll to top` | Moves to the top of the page. |
| **scrolltobottom**| `scroll down`, `scroll to bottom` | Moves to the bottom of the page. |
| **switchtoframe** | `switch to frame` | Moves driver focus inside an iFrame. |
| **drawpolygon** | `draw the polygon` | Specialized interaction for map elements. |
| **verifygridvalue**| `verify grid` | Checks values inside a data table/grid. |

**Excel Example:**

| Test Step Description | Action | Value | Target (XPath) |
| :--- | :--- | :--- | :--- |
| Map City Boundary | drawpolygon | -120;-120 : 120;-120 : 120;120 : -120;120 | //div[@class='map-container'] |

---

## 🎲 7. Dynamic Placeholders (Value Column)

| Placeholder | Result Example | Best For |
| :--- | :--- | :--- |
| **`{timestamp}`** | `301715` | Unique IDs/Names (Numbers only). |
| **`{randomAlpha}`** | `QWERTZ` | **Plumber Names** (Letters only). |
| **`{randomPhone}`** | `9845123456` | Valid 10-digit Indian Mobile format. |

**Excel Example:**

| Test Step Description | Action | Value                | Target (XPath) |
| :--- | :--- |:---------------------| :--- |
| Enter Unique Area | type | Chennai_{timestamp}  | //input[@id='areaName'] |
| Enter Plumber Name | type | Plumber{randomAlpha} | //input[@id='providerName'] |

> **⚠️ STRICT VALIDATION RULE:** If a field (like Plumber Name) does not allow numbers or underscores, use **`Plumber{randomAlpha}`** directly (No spaces, no symbols).

---

## 💾 8. Save & Reuse Logic

Capture a value in one step to use it in a later step.

### **How to Save:**

Use the `>>` operator in the **Value** column.
- **Action:** `type`
- **Value:** `NewUser{randomAlpha} >> savedName`
- *Result:* Generates `NewUserBQK`, types it, and stores it as "savedName".

### **How to Reuse:**

Wrap the variable name in curly braces `{}`.
- **Action:** `type`
- **Value:** `{savedName}`
- *Result:* Types the exact same value generated previously.

**Excel Example:**

| Step | Description | Action | Value | Target (XPath) |
| :--- | :--- | :--- | :--- | :--- |
| 1 | Create Store Name | type | KFC_{timestamp} >> storeName | //input[@id='sname'] |
| 2 | ...Other Steps... | ... | ... | ... |
| 3 | Filter Created Store | type | {storeName} | //input[@placeholder='Search'] |

---

## 📊 9. Reporting & Debugging
The framework is designed to make debugging easy:
1.  **Red Box Highlighting:** If a step fails, the report screenshot will show a **Red Border** around the specific element that failed.
2.  **Video Logs:** Check `test-outputs/videos` for a full recording of the execution.
3.  **Smart Errors:** The framework tells you if the error was a "Timeout" (Missing element) or a "Validation Error" (Form rejected).

---

## 🔗 10. Cross-Sheet Dependencies (Preconditions)

The framework supports **Recursive Dependencies**. If one test suite (Sheet) requires data or a state created in another sheet, you can link them directly within the Excel file.

### **How to Use**
In the **Precondition** column (Column 5) of the **very first test case row** (Row 2) of your sheet, use the keyword `RunSheet:` followed by the exact name of the required sheet.

**Excel Example (Inside "AddCityAdmin" sheet):**

| Test Case ID | ... | Precondition |
| :--- | :--- | :--- |
| TC_CA_02 | ... | **Data Dependency:** This test requires an existing area. <br><br> **RunSheet: AddCityArea** <br><br> **Authentication:** SuperAdmin session must be active. |

### **How It Works**
1.  **Detection:** The framework scans the Precondition cell for the `RunSheet:` trigger.
2.  **Recursion:** It automatically pauses the current sheet (`AddCityAdmin`), switches to the dependency sheet (`AddCityArea`), and executes all its steps.
3.  **Return:** Once the dependency sheet finishes, the framework automatically returns to the original sheet to continue the test.
4.  **Smart Execution:** To save time, if `AddCityArea` has already been completed earlier in the same execution run, the framework will recognize this and skip the redundant run.

> **💡 Pro-Tip:** You can include as much descriptive text as you like in the Precondition column (authentication steps, system requirements, etc.). The framework is smart enough to find the `RunSheet:` keyword hidden anywhere in that text.

---

## 💡 Best Practices
* **Relative Paths:** Use `src/main/resources/test-data/image.jpg` for uploads. Never use `C:\Users\...`.
* **Excel Locking:** **Always close your Excel file** before running a test to avoid file access errors.
* **Wait for Toasts:** Use a `wait for toast` or `wait 2` step after clicking "Save" to ensure the system processes the request.