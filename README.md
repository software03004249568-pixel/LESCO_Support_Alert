# LESCO Complaint Alert 4.0

Android monitoring app for LESCO IT Directorate support tickets.

## Final behavior
- URL and API key are entered once and saved locally.
- After first setup, monitoring starts automatically whenever the app is opened.
- Foreground monitoring checks for new pending complaints every 15 seconds.
- New complaints trigger a high-priority notification with sound and vibration.
- Pending complaints are listed in the app; tapping a complaint opens details and Resolve.
- Resolving a complaint removes it from the pending list after refresh.
- Monitoring restarts after device reboot when Android allows background startup.
- The app requests notification permission and battery-optimization exemption on setup.

## Important Android limitation
If the user explicitly uses Android **Force Stop** on the app, Android prevents background execution until the app is opened again. OEM-specific settings such as Infinix Auto-start may also need to be enabled by the user.
