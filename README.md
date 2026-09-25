# Complaint Alert

LESCO IT Directorate support ticket monitoring.

## Architecture

Support Ticket form / VB.NET / web client
        -> Google Apps Script doPost()
        -> Google Sheet
        -> Email notification to existing recipients
        -> Android app polls Apps Script doGet()
        -> Android notification for a new ticket

The Android app does NOT request Gmail read/notification-listener access.

## Existing email recipients preserved

- software03004249568@gmail.com
- waqas.tiwana@lesco.gov.pk

## GitHub Actions

Push this Android folder's contents to the root of the GitHub repository.

Then open:
Actions -> Build Complaint Alert APK

The APK is published as the workflow artifact `ComplaintAlert-debug`.

## Google Apps Script

Use `GoogleAppsScript/Code.gs`.

Before deployment:
1. Set a long random API_KEY.
2. Run setupTicketSystem().
3. Deploy as Web App.
4. Execute as yourself.
5. Access: Anyone.
6. Put the /exec URL and API key into the Android app.
