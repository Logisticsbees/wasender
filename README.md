# WaSender – WhatsApp Bulk Messaging Tool

An Android app that sends bulk WhatsApp messages using the Accessibility Service.

## Features
- 📤 Bulk messaging campaigns (same or per-contact messages)
- 📋 CSV import contacts (auto-detects phone/name/message columns)
- 📖 Phone book picker with multi-select
- 📎 Media attachments (image, video, files)
- 📊 Campaign reports with CSV/VCF export
- 📅 Schedule campaigns for future delivery
- ✏️ Message templates with placeholders `{name}` `{firstName}` `{phone}`
- 👥 Group number extractor (scrape participants from any WA group)
- 📞 Send to non-saved numbers
- ⏸ Pause / Resume / Skip / Stop controls
- 🔄 Auto-retry failed contacts

## Setup

### 1. Clone and build
```bash
git clone https://github.com/Logisticsbees/wasender
cd wasender
./gradlew assembleDebug
```

### 2. Install on device
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 3. Enable Accessibility Service
Settings → Accessibility → Downloaded Apps → WaSender → Enable

### 4. Grant permissions
- Contacts (for phone book import)
- Storage/Media (for CSV and media attachments)

## Build APK via GitHub Actions
Push to `main` branch → Actions tab → "Build WaSender APK" → Download artifact

## Architecture
```
WaSenderAccessibilityService  ← Controls WhatsApp UI (state machine)
       ↑ broadcasts
MessageSenderService           ← Foreground service, manages queue + delays
       ↑ calls
CampaignViewModel              ← Business logic + Room DB
       ↑
UI Fragments/Activities        ← Create campaigns, view reports, templates
```

## Important Notes
- **Use responsibly** – keep delays ≥5 seconds to avoid WhatsApp bans
- Test with your own numbers first
- WhatsApp may flag accounts sending >100 messages/day
- Works with both `com.whatsapp` and `com.whatsapp.w4b`

## CSV Format
```csv
phone,name,message
+911234567890,John,Hello {name}!
+919876543210,Jane,Hi {firstName}!
```
