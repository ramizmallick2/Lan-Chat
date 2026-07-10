# LanConnect

LanConnect is a powerful, offline-first Android application designed to keep you connected with others on your Local Area Network (LAN). No internet connection? No problem! This app utilizes Network Service Discovery (NSD) to automatically find peers on your Wi-Fi network and allows you to chat, make voice calls, and even video calls directly, securely, and seamlessly.

## Features

- **Zero Configuration:** Automatically discover other LanConnect users on your local network.
- **Real-time Chat:** Send and receive text messages instantly.
- **Voice Calling:** High-quality, low-latency audio calls directly over LAN.
- **Video Calling:** Face-to-face video calls without needing an active internet connection.
- **Offline First:** Operates entirely over local Wi-Fi. No data plan or active internet connection required.
- **Modern UI:** Built entirely with Jetpack Compose and Material Design 3 for a beautiful and responsive user experience.
- **Privacy Focused:** All communications stay strictly on your local network. No third-party servers, no cloud storage, and no data collection.

## Tech Stack

- **Language:** Kotlin
- **UI Toolkit:** Jetpack Compose (Material 3)
- **Networking:** Network Service Discovery (NSD), DatagramSocket (UDP for audio streaming), ServerSocket/Socket (TCP for text & video frames)
- **Media:** AudioRecord/AudioTrack for audio streaming, CameraX for video capture.
- **Architecture:** MVVM (Model-View-ViewModel) with StateFlow and Coroutines.

## How It Works

1. **Discovery:** When you launch the app, it broadcasts its presence using Android's NSD manager. It also continuously listens for other devices broadcasting the LanConnect service.
2. **Chatting:** When you connect to a peer, the app opens a direct TCP socket connection to instantly transmit JSON-encoded text messages.
3. **Calling:** 
    - **Voice:** Streams PCM audio via UDP packets to ensure low latency and real-time conversation.
    - **Video:** Leverages CameraX to capture frames, compresses them to JPEG on the fly, and streams them via a dedicated TCP socket to the connected peer.

## Permissions Required

The app requests the following permissions to function correctly:
- **Camera:** Required for capturing video during video calls.
- **Microphone (Record Audio):** Required for capturing your voice during voice and video calls.
- **Network State & Wi-Fi State:** Required to discover and connect to other devices on the network.

## Getting Started

1. Clone or download this repository.
2. Open the project in Android Studio.
3. Build and run the app on two or more Android devices connected to the **same Wi-Fi network**.
4. Set up your username on the welcome screen.
5. Tap on a discovered peer in the list to start chatting or calling!

## Author

Created and developed entirely by me. All rights reserved.
