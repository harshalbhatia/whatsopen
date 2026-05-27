# WhatsOpen

Open WhatsApp chats with any phone number — even numbers not in your contacts.

Android app that lets you start a WhatsApp conversation by entering a phone number, pasting from the clipboard, or picking from your recent call log. No need to save the contact first.

## Features

- Enter a phone number with country code and open WhatsApp directly
- Detect and use phone numbers from clipboard
- Browse recent call log entries and open WhatsApp for any of them
- Filter call log by call type and WhatsApp contact status
- Material 3 design, light/dark theme

## Requirements

- Android 7.0 (API 24) or higher
- WhatsApp installed

## Permissions

- `READ_CALL_LOG` — to show recent calls
- `READ_CONTACTS` — to display contact names alongside numbers

No network access. No analytics. No tracking.

## Building

```sh
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## License

Licensed under the [Apache License 2.0](LICENSE).
