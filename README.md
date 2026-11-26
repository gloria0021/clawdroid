# Clawdroid

English | [日本語](README.ja.md)

An unofficial Android client for [OpenClaw](https://github.com/openclaw/openclaw), built to make AI coding with OpenClaw comfortable on a phone. The UI is designed around the file tree, file viewer, terminal, and easy parallel development with worktrees.

Note: This app does not aim to cover all OpenClaw features.

[Watch the demo video](docs/media/demo-movie.mp4)

## Supported versions

- OpenClaw Gateway: 2026.11.10
- Android 13 or later

## Features

- Native app with Material 3 Expressive design
- Theme support
- Connect to your Gateway (Tailscale recommended)
- Quick access to each agent from the side panel
- Agent settings
- Session view
- Switch models and reasoning effort; check usage and host resources
- Create session shortcuts on your home screen
- File tree (date / size / upload / create files and folders)
- File viewer (view / edit / delete)
- Terminal
- Parallel development button (quickly create a worktree)
- View, configure, and run automations
- Notifications

## Screenshots

Screenshots show the English interface. The app also supports Japanese.

### Settings and themes

<p>
  <img src="docs/media/settings.png" width="220" alt="Appearance, theme, and transparency settings">
</p>

<details>
<summary>Compare light and dark themes</summary>


| Theme | Light | Dark |
| --- | --- | --- |
| OpenClaw | <img src="docs/media/theme-openclaw-light.png" width="220" alt="OpenClaw — light"> | <img src="docs/media/theme-openclaw-dark.png" width="220" alt="OpenClaw — dark"> |
| Monochrome | <img src="docs/media/theme-monochrome-light.png" width="220" alt="Monochrome — light"> | <img src="docs/media/theme-monochrome-dark.png" width="220" alt="Monochrome — dark"> |
| Dynamic Color | <img src="docs/media/theme-dynamic-light.png" width="220" alt="Dynamic Color — light"> | <img src="docs/media/theme-dynamic-dark.png" width="220" alt="Dynamic Color — dark"> |
| Custom | <img src="docs/media/theme-custom-light.png" width="220" alt="Custom — light"> | <img src="docs/media/theme-custom-dark.png" width="220" alt="Custom — dark"> |

</details>

### Side panel

<p>
  <img src="docs/media/side-panel-chat.png" width="220" alt="Chat list">
  <img src="docs/media/side-panel-dev.png" width="220" alt="Sessions grouped by project">
  <img src="docs/media/side-panel-agent-settings.png" width="220" alt="Settings for agents shown in the side panel">
</p>

### Conversations, models, and usage

<p>
  <img src="docs/media/session-menu.png" width="220" alt="Session menu">
  <img src="docs/media/session-model-usage.png" width="220" alt="Model, usage, and host resources">
</p>

### Files, terminal, and parallel development

<p>
  <img src="docs/media/file-tree-menu.png" width="220" alt="File tree and actions">
  <img src="docs/media/file-editor.png" width="220" alt="File viewer and editor">
  <img src="docs/media/terminal.png" width="220" alt="Terminal">
</p>

<p>
  <img src="docs/media/parallel-work-create.png" width="220" alt="Create a worktree for parallel development">
</p>

### Agent settings

<p>
  <img src="docs/media/agent-overview.png" width="220" alt="Agent overview">
  <img src="docs/media/agent-persona.png" width="220" alt="Agent persona files">
  <img src="docs/media/agent-skills.png" width="220" alt="Skill management">
</p>

### Automations

<p>
  <img src="docs/media/automation-list.png" width="220" alt="Automation list">
  <img src="docs/media/automation-details.png" width="220" alt="Automation details and execution settings">
</p>

### Home screen shortcuts

<p>
  <img src="docs/media/launcher-shortcuts.png" width="220" alt="Launcher shortcuts">
  <img src="docs/media/launcher-shortcut-editor.png" width="220" alt="Shortcut editor">
</p>

## Privacy Policy

[Privacy Policy](docs/privacy-policy.md) (Japanese / English)

## License

[MIT License](LICENSE)

Licenses of the bundled libraries, fonts, and icons are available in the app under Settings → About → Open source licenses. `reference/openclaw/` is a copy of source and documentation from [OpenClaw](https://github.com/openclaw/openclaw), covered by its own MIT License (`reference/openclaw/LICENSE`).
