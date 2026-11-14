# WhatsOpen

Open WhatsApp chats with any phone number — even numbers not in your contacts.

Android app that lets you start a WhatsApp conversation by typing a phone number, pasting text that contains one, or picking a recent call. No need to save the contact first.

## Features

- Type a number and open the chat; your country code is filled in
- Paste text with phone numbers in it and pick the one to open
- Open a chat with a recent caller, with filters for incoming, missed and non-contact calls
- Share text, tap a `tel:` link, or select a number in any app to open its chat
- Paste & Open from a home screen shortcut or a Quick Settings tile
- Works with WhatsApp and WhatsApp Business
- Material You colours, dark theme and a themed icon

## Requirements

- Android 7.0 (API 24) or higher
- WhatsApp or WhatsApp Business installed

## Permissions

- `READ_CALL_LOG` (optional) — to show recent calls. Names come from the call log's own cache; contacts aren't read.

No network access. No analytics. No tracking.

## Building

```sh
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`. Debug builds install as "WhatsOpen Debug" (`com.harshalbhatia.whatsopen.debug`), beside the release app.

Tests:

```sh
./gradlew lintDebug testDebugUnitTest
```

## License

Licensed under the [Apache License 2.0](LICENSE).
