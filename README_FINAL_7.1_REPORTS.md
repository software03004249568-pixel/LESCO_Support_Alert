# LESCO Complaint Alert — FINAL 7.1 Reports Update

This package is based on the working FINAL 7.0 FCM Android project and the latest `Code_modified_mail_authorization.zip` Apps Script backend. The only functional addition is the date-range Complaint Reports feature.

## What was added
- A `Complaint Reports • Date Range & Export` button on the existing mobile dashboard.
- Inclusive From Date / To Date filtering using the complaint's `Date` column.
- Report summary counts: Total, Pending, Resolved (includes Closed).
- Full complaint details including Ticket No, complaint date, sub division, operator, mobile, issue type, issue, priority, status, Closed By, Closed Date, resolution, and remarks.
- Export to CSV with UTF-8 BOM, suitable for opening in Microsoft Excel.
- A separate Apps Script `action=report` endpoint. Existing dashboard, complaint entry, email, FCM, resolve, and background-monitoring flows are left in place.
- Android version bumped to 7.1 / versionCode 8 so it can be installed as an update over 7.0. The package name remains `com.complaintalert`, so app preferences should be retained when updating rather than uninstalling.

## Step 1 — Update Google Apps Script
1. Open the same Apps Script project currently used by the working app.
2. Make a backup copy of its current `Code.gs` first.
3. Replace `Code.gs` with `GoogleAppsScript/Code.gs` from this package. This file is based on the latest mail-authorization code and preserves its configured Firebase project ID and existing settings.
4. Do **not** change the current API key, spreadsheet ID, email recipients, or Firebase project ID.
5. Save the project.
6. Deploy → Manage deployments → edit the existing Web App deployment → select **New version** → Deploy. Keep the existing Web App URL and access/execute settings. If you create a new deployment instead, update the URL saved in the mobile app configuration.
7. No new OAuth scope is required for the report endpoint; it reads the same spreadsheet already used by the existing API.

## Step 2 — Prepare Android project
1. Open the `Android` folder in Android Studio.
2. Copy the same working Firebase `google-services.json` into `Android/app/google-services.json`. It is intentionally not included in this source ZIP; use the file belonging to Firebase project `lesco-complaint-alert` and package `com.complaintalert`.
3. Sync Gradle and build the APK (Build → Build APK(s)).
4. Install the new APK over the existing app. Do not uninstall the existing app, because uninstalling clears locally saved URL/API key/user name.

## Step 3 — Use reports
1. Open the app and let it connect as usual.
2. Tap **Complaint Reports • Date Range & Export**.
3. Choose From Date and To Date, then tap **Generate Report**.
4. Review the totals and complaint details.
5. Tap **Export CSV (opens in Excel)** and choose a save location in the Android file picker.

## Notes
- The report uses the complaint `Date` field, not the timestamp when a row was edited or resolved.
- `Closed By` and `Closed Date` are shown when they are stored in the sheet. Historical records without those values will show `-`.
- The report returns all rows in the selected date range; for a very large sheet, loading/exporting may take longer.
- No splash-screen change was made, to avoid introducing unrelated startup behavior into the working notification app.

## Validation performed
- Confirmed the new Android button, activity declaration, and report API action are present in source.
- XML files and Apps Script syntax are checked as part of packaging where local validation tools are available.
- A full Android Gradle build was not run in this environment because Gradle/Android SDK are not installed here. Please build in Android Studio or through your existing GitHub Actions workflow.
