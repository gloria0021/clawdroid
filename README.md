# SumahoClaw

English | [日本語](README.ja.md)

An unofficial Android client for [OpenClaw](https://github.com/openclaw/openclaw), built to make AI coding with OpenClaw comfortable on a phone. The UI is designed around the file tree, file viewer, terminal, and easy parallel development with worktrees.
> This app does not aim to cover all OpenClaw features.

https://github.com/user-attachments/assets/460b1cbb-cf53-4374-a51c-96633a2e692d

## Supported versions

- OpenClaw Gateway: 2026.11.10
- Android 13 or later

## Features

A native app with Material 3 Expressive design.

### Themes

| Theme name | Light | Dark |
| --- | --- | --- |
| OpenClaw | <img src="docs/media/theme-openclaw-light.png" width="220" alt="OpenClaw — light"> | <img src="docs/media/theme-openclaw-dark.png" width="220" alt="OpenClaw — dark"> |
| Monochrome | <img src="docs/media/theme-monochrome-light.png" width="220" alt="Monochrome — light"> | <img src="docs/media/theme-monochrome-dark.png" width="220" alt="Monochrome — dark"> |
| Dynamic (Wallpaper) | <img src="docs/media/theme-dynamic-light.png" width="220" alt="Dynamic Color — light"> | <img src="docs/media/theme-dynamic-dark.png" width="220" alt="Dynamic Color — dark"> |
| Custom | <img src="docs/media/theme-custom-light.png" width="220" alt="Custom — light"> | <img src="docs/media/theme-custom-dark.png" width="220" alt="Custom — dark"> |

### Connection

Enter your Gateway URL and token, then approve the connection. Tailscale is recommended.

<p>
  <img src="docs/media/gateway-connection.png" width="220" alt="Gateway connection screen">
</p>

### Side panel

- Switch between Chat and Dev modes.
- Check Git diffs, branches, and worktrees in Dev mode.
- Quickly switch between up to four agents using tabs.

<p>
  <img src="docs/media/side-panel-chat.png" width="220" alt="Chat list">
  <img src="docs/media/side-panel-dev.png" width="220" alt="Sessions grouped by project">
  <img src="docs/media/side-panel-agent-settings.png" width="220" alt="Settings for agents shown in the side panel">
</p>

### Session view

- View session conversations and access features from the session menu.
- Switch models and reasoning effort, and check usage, host memory, and disk space.

<p>
  <img src="docs/media/session-menu.png" width="220" alt="Session menu">
  <img src="docs/media/session-model-usage.png" width="220" alt="Model, usage, and host resources">
</p>

### Files, terminal, and parallel development

- Browse file dates and sizes in the file tree, upload files, and create files and folders.
- View, edit, and delete files in the file viewer.
- Use the terminal.
- Quickly create a worktree with the parallel development button.

<p>
  <img src="docs/media/file-tree-menu.png" width="220" alt="File tree and actions">
  <img src="docs/media/file-editor.png" width="220" alt="File viewer and editor">
  <img src="docs/media/terminal.png" width="220" alt="Terminal">
  <img src="docs/media/parallel-work-create.png" width="220" alt="Create a worktree for parallel development">
  <img src="docs/media/parallel-work-finish.png" width="220" alt="Finish parallel development: merge or discard">
</p>

### Agent settings

View agent details, personality, and skills.

<p>
  <img src="docs/media/agent-overview.png" width="220" alt="Agent overview">
  <img src="docs/media/agent-persona.png" width="220" alt="Agent persona files">
  <img src="docs/media/agent-skills.png" width="220" alt="Skill management">
</p>

### Automations

View, edit, and run automations.

<p>
  <img src="docs/media/automation-list.png" width="220" alt="Automation list">
  <img src="docs/media/automation-details.png" width="220" alt="Automation details and execution settings">
</p>

### Home screen shortcuts

Create and edit shortcuts to agents and sessions for quick access from your home screen.

<p>
  <img src="docs/media/launcher-shortcuts.png" width="220" alt="Launcher shortcuts">
  <img src="docs/media/launcher-shortcut-editor.png" width="220" alt="Shortcut editor">
</p>

## Privacy Policy

[Privacy Policy](docs/privacy-policy.md) (Japanese / English)

## License

[MIT License](LICENSE)

Licenses of the bundled libraries, fonts, and icons are available in the app under Settings → About → Open source licenses. `reference/openclaw/` is a copy of source and documentation from [OpenClaw](https://github.com/openclaw/openclaw), covered by its own MIT License (`reference/openclaw/LICENSE`).
