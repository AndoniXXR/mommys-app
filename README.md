# Mommys - e621/e926 Android Client

A modern Android client for browsing e621.net and e926.net, inspired by "The Wolf's Stash" app but rebuilt from scratch in Kotlin.

## Features

### Core Features
- Browse and search posts by tags with autocomplete suggestions
- Modern Material 3 design with dark/light theme support
- Favorites and saved posts management
- Download images, GIFs and videos
- PIN lock and biometric authentication for privacy
- Advanced video playback system with intelligent codec handling
- Offline support with Room database caching and video cache

### Post Viewing
- Swipe navigation between posts with ViewPager2
- Full resolution image viewing with zoom and pan
- GIF animation support with play/pause controls
- Video playback with ExoPlayer featuring:
  - Smart codec support with automatic VP9 to MP4 fallback
  - 100MB video cache for improved performance
  - Software decoder fallback for unsupported formats
  - Preview thumbnails with manual playback initiation
  - Loop, mute, speed controls, and progress tracking
  - Graceful handling of unsupported videos with browser fallback
- Tag display organized by category (artist, character, species, etc.)
- Quick actions: favorite, vote up/down, download, share

### Additional Features
- Pool browsing and navigation
- Set browsing and search
- Wiki page viewer
- Comments viewing
- User profile viewing
- Edit post tags and sources (for authorized users)
- News and changelog screens
- Network connectivity monitoring with auto-retry on reconnection
- Automatic update checking from GitHub releases

## Requirements

- Android 7.0 (API 24) or higher
- Android Studio Hedgehog or newer

## Building

1. Clone this repository
2. Open in Android Studio
3. Sync Gradle files
4. Build and run on your device/emulator

```bash
./gradlew assembleDebug
```

## Project Structure

```
app/
├── src/main/
│   ├── java/com/mommys/app/
│   │   ├── data/
│   │   │   ├── api/          # Retrofit API service
│   │   │   ├── database/     # Room database
│   │   │   ├── model/        # Data classes
│   │   │   ├── preferences/  # SharedPreferences manager
│   │   │   ├── repository/   # Repository pattern
│   │   │   └── search/       # Search suggestions provider
│   │   ├── ui/
│   │   │   ├── about/        # About screen
│   │   │   ├── blacklist/    # Blacklist management
│   │   │   ├── browse/       # Browse tags and pools
│   │   │   ├── changelog/    # Changelog viewer
│   │   │   ├── comments/     # Comments activity
│   │   │   ├── donate/       # Donation links
│   │   │   ├── downloads/    # Download manager
│   │   │   ├── edit/         # Edit post activity
│   │   │   ├── following/    # Following posts
│   │   │   ├── launcher/     # Splash screen
│   │   │   ├── login/        # Login with API key
│   │   │   ├── main/         # Main grid view
│   │   │   ├── news/         # News viewer
│   │   │   ├── notes/        # Image notes
│   │   │   ├── pin/          # PIN lock screen
│   │   │   ├── pool/         # Pool viewer
│   │   │   ├── popular/      # Popular posts
│   │   │   ├── post/         # Post detail view with pager
│   │   │   ├── profile/      # User profile
│   │   │   ├── saved/        # Saved searches
│   │   │   ├── sets/         # Set browser
│   │   │   ├── settings/     # Settings screen
│   │   │   ├── views/        # Custom views
│   │   │   ├── webview/      # WebView activities
│   │   │   └── wiki/         # Wiki page viewer
│   │   ├── util/
│   │   │   └── network/      # Network monitoring system
│   │   └── MommysApplication.kt
│   └── res/
│       ├── layout/           # XML layouts
│       ├── drawable/         # Vector icons
│       ├── mipmap/           # App icons
│       ├── menu/             # Menu resources
│       ├── values/           # Strings, colors, themes
│       └── xml/              # Network security config
```

## Technologies

- **Language:** Kotlin
- **UI:** Material 3, ViewBinding, ConstraintLayout
- **Architecture:** MVVM with LiveData and StateFlow
- **Networking:** Retrofit 2, OkHttp with cookie handling
- **Database:** Room with migrations
- **Image Loading:** Glide with GIF support
- **Video:** Media3 ExoPlayer with custom cache manager
- **Async:** Kotlin Coroutines and Flow
- **Security:** Biometric authentication, encrypted preferences

## Video Playback System

The app features an advanced video playback system designed for optimal compatibility and performance:

### Key Features
- **Intelligent Codec Handling:** Automatically detects VP9 codec issues and falls back to MP4 when available
- **100MB Video Cache:** Implements LRU caching with ExoPlayer's CacheDataSource for smooth playback and reduced bandwidth
- **Software Decoder Fallback:** Uses DefaultRenderersFactory with EXTENSION_RENDERER_MODE_PREFER to handle devices without hardware codec support
- **Preview-First Approach:** Shows video thumbnails with play buttons, creating player instances only on user interaction to conserve memory
- **Graceful Error Handling:** For truly unsupported videos, offers option to view in browser rather than showing technical errors

### Architecture
- **ExoPlayerCacheManager:** Singleton cache manager with SimpleCache and LeastRecentlyUsedCacheEvictor
- **Smart Player Lifecycle:** Players are created on-demand, released on view recycling, and properly managed in ViewPager2
- **Format Selection:** Supports quality preferences (original/720p/480p) and format preferences (webm/mp4) with intelligent fallback

## API

This app uses the e621/e926 REST API. You need an account and API key to access authenticated features like favorites, voting, and editing.

Get your API key from: https://e926.net/users/home (Manage API Access)

## Network Monitoring

The app includes a network monitoring system that:
- Detects connection state changes (online/offline)
- Shows status banners when offline or on slow connections
- Automatically retries failed requests when connection is restored
- Adapts thread count based on network type (WiFi vs mobile)

## License

This project is for educational purposes only.

## Disclaimer

This is an unofficial client. We are not affiliated with e621.net or e926.net.
