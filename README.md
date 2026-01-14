# Mommys - e926 Android Client

A modern Android client for browsing e926.net, inspired by "The Wolf's Stash" app.

## Features

- 🔍 Browse and search posts by tags
- 📱 Modern Material 3 design
- 🌙 Dark mode support
- ❤️ Favorites and saved posts
- 📥 Download images and videos
- 🔐 PIN lock for privacy
- 🎥 Video playback with ExoPlayer
- 💾 Offline support with Room database

## Requirements

- Android 7.0 (API 24) or higher
- Android Studio Hedgehog or newer

## Building

1. Clone this repository
2. Open in Android Studio
3. Sync Gradle files
4. Build and run on your device/emulator

## Project Structure

```
app/
├── src/main/
│   ├── java/com/mommys/app/
│   │   ├── data/
│   │   │   ├── api/          # Retrofit API service
│   │   │   ├── database/     # Room database
│   │   │   ├── model/        # Data classes
│   │   │   ├── preferences/  # SharedPreferences
│   │   │   └── repository/   # Repository pattern
│   │   ├── ui/
│   │   │   ├── launcher/     # Splash screen
│   │   │   ├── login/        # Login with API key
│   │   │   ├── main/         # Main grid view
│   │   │   ├── pincode/      # PIN lock screen
│   │   │   ├── post/         # Post detail view
│   │   │   └── settings/     # Settings screen
│   │   └── MommysApplication.kt
│   └── res/
│       ├── layout/           # XML layouts
│       ├── drawable/         # Vector icons
│       ├── values/           # Strings, colors, themes
│       └── xml/              # Network security config
```

## Technologies

- **Language:** Kotlin
- **UI:** Material 3, ViewBinding
- **Architecture:** MVVM
- **Networking:** Retrofit 2, OkHttp
- **Database:** Room
- **Image Loading:** Glide
- **Video:** Media3 ExoPlayer
- **Async:** Kotlin Coroutines

## API

This app uses the e621/e926 REST API. You need an account and API key to access authenticated features.

Get your API key from: https://e926.net/users/home (Manage API Access)

## License

This project is for educational purposes only.

## Disclaimer

This is an unofficial client. We are not affiliated with e621.net or e926.net.
