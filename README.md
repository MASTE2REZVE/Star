# 🌟 Star: All-in-One AI Agent, Video Editor & 2D Animation Studio

**Star** is a native Android application built entirely on-device using Termux. It combines a powerful AI agent, a professional video editor (powered by Concat), and a GPU-accelerated 2D animation studio.

## 🚀 Features
- **🤖 AI Agent:** Run local or cloud LLMs (OpenRouter, OpenCode, etc.) to automate tasks, write code, and control the device.
- **🎬 Video Editor:** A full NLE timeline powered by the Concat video engine. Supports 170+ effects, transitions, and 4K export.
- **🎨 2D Studio:** A frame-by-frame animation suite featuring OpenGL ES 3.0 GPU rendering, multi-layer support, onion skinning, and tile-based canvas.
- **🛠️ The Harness:** A built-in autonomous AI task-runner that builds and installs features 24/7.

## 📸 Screenshots
*(Coming soon - The 2D Studio UI is currently in active development)*

## 🏗️ Architecture
Star is built with a modular architecture:
- **UI:** Java/Kotlin (Programmatic UI, No Material Design, Dark Theme).
- **2D Engine:** OpenGL ES 3.0 (Custom GLSL shaders, Tile-based canvas).
- **Video Engine:** Rust/C++ (Concat CLI bridge).
- **Build System:** Gradle 8.10.2 & Android SDK built in Termux.

## 🛠️ Building from Source
See [docs/STAR_LONG_STATE.md](docs/STAR_LONG_STATE.md) for full build instructions.

## 💖 Support
If you find this project useful, please consider supporting its development.
See [DONATE.md](DONATE.md) for crypto addresses.

## 📄 License
GPL-3.0
