# LESCO Complaint Alert 3.0

Android monitoring app for the LESCO IT Directorate support ticket Google Sheet.

## What this version does

- Runs monitoring through an Android foreground service.
- Checks the Google Apps Script API every 60 seconds.
- Shows **all pending complaints** in the main screen.
- Pending means `New`, `In Progress`, or `Pending`; `Resolved` and `Closed` are hidden from the pending list.
- Each complaint shows Ticket No, Issue/Nature, Status, Sub Division and Priority.
- Tap any complaint to open full details.
- Tap **Resolve** to change the Google Sheet status to `Resolved`.
- After a successful resolve, the complaint immediately disappears from the pending list and the counters refresh.
- Sends an Android notification when a newly registered pending complaint is detected.
- Restarts monitoring after device boot when configuration has been saved.

## Backend

Use `../GoogleAppsScript/Code.gs`. It adds these API actions:

- `GET action=pending` — returns only unresolved/unclosed complaints.
- `GET action=resolve&ticketNo=...` — marks a complaint as Resolved.
- Existing `tickets`, `ticket`, `stats`, and ticket-creation APIs remain available.

After replacing the Apps Script code, deploy a **new Web App version**. Use the resulting `/exec` URL in the Android app.

## Build APK with GitHub Actions

Push the contents of this `Android` folder to the root of a GitHub repository. Then run:

`Actions -> Build Complaint Alert APK`

The generated debug APK is uploaded as the `ComplaintAlert-debug` artifact.
