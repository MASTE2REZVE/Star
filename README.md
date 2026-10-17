# 🌟 Star: All-in-One AI Agent & Creative Studio (Alpha)

**Star** is an experimental, on-device Android application built entirely in Termux. It aims to combine an AI agent, a video editor, and a 2D animation studio into a single app.

> ⚠️ **Project Status: Alpha / Work In Progress**
> This project is currently under heavy active development by an autonomous AI agent. Many features are actively being built, stabilized, and optimized. If you are looking for a stable daily driver, wait for the v2.0 Beta release.

## 🚧 Current Development Status
- [x] **AI Agent:** Chat, OpenRouter/OpenCode integration, multi-provider fallback.
- [x] **Video Engine:** Concat CLI bridge compiled for Android (JSON-RPC).
- [x] **2D Studio UI:** OpenGL ES 3.0 shell, tile-based canvas, layer panel, timeline.
- [ ] **2D Drawing Engine:** Touch input and brush stroke rendering (In Progress).
- [ ] **Animation Engine:** Playback, onion skinning (Stabilizing).
- [ ] **Rigging Engine:** Bones and mesh deform (Planned).

## 📸 Screenshots
*(Will be added once the drawing canvas is fully stable.)*

## 🏗️ Architecture
- **UI:** Java/Kotlin (Programmatic UI, Dark Theme).
- **2D Engine:** OpenGL ES 3.0 (Custom GLSL shaders).
- **Video Engine:** Rust/C++ (Concat bridge).
- **Build System:** Gradle & Android SDK inside Termux.

## 🛠️ Building from Source
See [docs/STAR_LONG_STATE.md](docs/STAR_LONG_STATE.md) for build instructions.

## 💖 Support
See [DONATE.md](DONATE.md) for crypto addresses.

## 📄 License
This project is licensed under the [GPL-3.0 License](LICENSE).
