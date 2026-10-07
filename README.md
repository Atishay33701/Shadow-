# SHADOW V1

A phone-first Android personal assistant with a HUD-style interface.

## What this version does

- Dark cyan HUD inspired by the supplied reference image.
- Voice input using Android SpeechRecognizer.
- Android Text-to-Speech voice output.
- Safe, whitelisted local commands:
  - time
  - battery
  - open Settings
  - open YouTube / Chrome / WhatsApp / Instagram / Telegram / Calculator
- Unknown questions go to Gemini.
- Gemini is configured with Google Search grounding so SHADOW can answer current questions when the model uses search.
- API key is entered inside the app and stored locally in SharedPreferences.
- No API key is hard-coded into the source.
- AI is NOT allowed to execute arbitrary phone commands. Device actions remain a local whitelist.

## Gemini setup

Create a Gemini API key in Google AI Studio, then open SHADOW and tap the gear icon.

Recommended model in this project:
`gemini-3.5-flash-lite`

The Gemini free tier is subject to Google's current quotas and availability. Search grounding has its own limits.

## Build on GitHub

This repo includes a GitHub Actions workflow. Push the project to GitHub, then open:

Actions -> Build SHADOW APK -> Run workflow

The generated debug APK is uploaded as a workflow artifact.

## Important

Never commit your Gemini API key to GitHub. If a key is ever exposed publicly, revoke/rotate it.

## Android

- minSdk 24
- targetSdk 37
- compileSdk 37
- AGP 9.4.0
- Java 17 source/target
