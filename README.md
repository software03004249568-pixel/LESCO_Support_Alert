# LESCO Complaint Alert — Final 5.0

Android app for LESCO IT Directorate support ticket monitoring.

## Features
- First-run URL + API key setup; values are stored locally on the phone.
- Automatic monitoring after configuration.
- Dashboard uses one `dashboard` API request for reliable loading.
- Pending complaints are shown as cards; resolved/closed tickets disappear from the pending list.
- Complaint details and Resolve action.
- Foreground background monitoring with high-priority notification, sound and vibration.
- Boot receiver restarts monitoring after device reboot when configuration exists.
- Custom launcher/notification icon.
- Battery optimization exemption request on supported Android versions.

## GitHub layout
Upload the **contents of this Android folder** to the repository root, so `app/` and `.github/` are at the repository root.

## Apps Script
Deploy `GoogleAppsScript/Code.gs` as a Web App. Keep the same `/exec` URL after updating the deployment. The API key must match the key in the Android app.
