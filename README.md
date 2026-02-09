# ACP Manager for IntelliJ IDEA

<!-- Plugin description -->
Manage multiple ACP (Agent Client Protocol) compatible coding agents directly from your IDE.

ACP Manager provides multi-agent session management, real-time streaming chat interface, tool call visualization, and efficient process lifecycle management.

Supports Claude Code, Codex CLI, Gemini CLI, and any ACP-compatible agents. Automatically detects agents from AutoDev/Xiuper configurations.
<!-- Plugin description end -->

## Features

- 🎯 **Auto-Detection** - Automatically discovers agents from `~/.autodev/config.yaml` (AutoDev/Xiuper integration)
- 🔄 **Multi-agent session management** - Run multiple agents simultaneously
- 💬 **Real-time streaming chat** - See agent responses as they stream in
- 🛠️ **Tool call visualization** - Monitor tool executions with clear status indicators
- 🔌 **Process lifecycle management** - Efficient process reuse and health monitoring
- ⚙️ **YAML-based configuration** - Easy agent setup and management
- 📊 **Beautiful UI** - Clean chat interface with role-specific message styling

## Supported Agents

Any ACP-compatible agent, including:
- **Claude Code** (Anthropic)
- **Codex CLI** (OpenAI)
- **Gemini CLI** (Google)
- And any custom ACP agents

## Installation

### Quick Start (3 Steps!)

1. **Install the plugin** in IntelliJ IDEA 2025.2+
   - Download from JetBrains Marketplace (coming soon) or build from source
   - Install via Settings → Plugins → Install from Disk

2. **Open ACP Manager** tool window
   - Find it in the right sidebar
   - Or: View → Tool Windows → ACP Manager

3. **Start chatting immediately!**
   - The Welcome page shows detected agents and an input area
   - Select an agent from dropdown (e.g., "kimi", "claude")
   - Type your message and press Enter
   - Agent auto-connects and responds!

**No configuration needed** if you have:
- AutoDev/Xiuper with `~/.autodev/config.yaml`, or
- ACP CLI tools in your system PATH

## Configuration

ACP Manager uses a smart 3-tier configuration system with automatic agent detection:

### Configuration Priority

```
Highest: ~/.acp-manager/config.yaml (your manual configurations)
   ↓
Middle:  ~/.autodev/config.yaml (AutoDev/Xiuper shared config)
   ↓
Lowest:  System PATH (auto-detected CLI tools - only for missing agents)
```

### 1. Automatic Detection from AutoDev/Xiuper

If you already use [AutoDev](https://github.com/unit-mesh/auto-dev) or [Xiuper](https://github.com/unit-mesh/xiuper), ACP Manager will **automatically use** your existing agent configurations from `~/.autodev/config.yaml`:

```yaml
# ~/.autodev/config.yaml (your existing config)
acpAgents:
  opencode:
    name: "OpenCode"
    command: "/Users/phodal/.opencode/bin/opencode"
    args: "acp"
    env: ""
  kimi:
    name: "Kimi"
    command: "/Library/Frameworks/Python.framework/Versions/3.12/bin/kimi"
    args: "acp"
    env: ""
  claude:
    name: "Claude Code"  
    command: "/opt/homebrew/bin/claude"
    args: ""
    env: ""
activeAcpAgent: kimi
```

**These agents will be used as-is, with their full configured paths.**

### 2. Auto-Detection for Missing Agents

For agents **NOT** in your AutoDev config, ACP Manager will automatically detect them from your system PATH:

- Checks common ACP CLI tools: `codex`, `gemini`, `copilot`, `auggie`, etc.
- Uses `which` (Unix/macOS) or `where` (Windows) to find full paths
- Only adds agents that are missing from your existing config

### 3. Manual Configuration

Create or edit `~/.acp-manager/config.yaml` to add custom agents or override any settings:

```yaml
# Active agent (optional - can be set via UI)
activeAgent: codex

# Available agents
agents:
  codex:
    command: codex
    args: ["--full-auto"]
    description: "OpenAI Codex CLI"
    autoApprove: false  # Auto-approve permission requests
    env:
      OPENAI_API_KEY: "your-api-key-here"
  
  claude:
    command: claude
    args: []
    description: "Anthropic Claude Code"
    autoApprove: true
  
  gemini:
    command: gemini
    args: ["--mode", "code"]
    description: "Google Gemini CLI"
    env:
      GOOGLE_API_KEY: "your-api-key-here"
```

**Note:** 
- Agents in `~/.acp-manager/config.yaml` override AutoDev config
- Agents in `~/.autodev/config.yaml` are used as configured (with full paths)
- Auto-detection only fills in **missing** agents from system PATH

### Configuration Options

- **command**: The executable command for the agent
- **args**: Command-line arguments (optional)
- **description**: Human-readable description shown in UI
- **autoApprove**: Automatically approve permission requests (default: false)
- **env**: Environment variables for the agent process

## Usage

### Quick Start

1. Open the **ACP Manager** tool window (View → Tool Windows → ACP Manager)
2. Click **+ Add Agent** to create an agent configuration, or manually edit `~/.acp-manager/config.yaml`
3. Select an agent from the dropdown
4. Click **Connect**
5. Start chatting!

### Multi-Agent Sessions

- Connect to different agents simultaneously - each gets its own tab
- Switch between agents using the toolbar or tabs
- Each session maintains independent conversation history
- Agent selector in input area allows quick switching within a session

### Input Area

- **Type your message** in the text area
- **Enter** to send (Shift+Enter for newline)
- **Agent selector** (bottom-left) - switch agents on the fly
- **Send button** (bottom-right) - or press Enter
- **Stop button** - cancel ongoing requests

### Message Types

- 💬 **User** - Your messages (blue)
- 🤖 **Assistant** - Agent responses (gray)
- ⚡ **Tool Call** - Agent executing tools (orange)
- ✅ **Tool Result** - Tool execution results (green/red)
- 💭 **Thinking** - Agent's internal reasoning (purple)
- ℹ️ **Info** - System messages (cyan)
- ⚠️ **Error** - Error messages (red)

## Architecture

### Core Components

- **AcpClient** - JSON-RPC over stdio transport
- **AcpProcessManager** - Process lifecycle and reuse
- **AcpSessionManager** - Multi-session management with observable state
- **AcpConfigService** - YAML configuration management

### Protocol Support

Full ACP 1.0 protocol implementation:
- ✅ Session initialization and management
- ✅ Prompt streaming with real-time updates
- ✅ Tool call execution (file I/O, terminal commands)
- ✅ Permission request handling
- ✅ Multi-turn conversations
- ✅ Session mode switching
- ✅ MCP server integration (optional)

## Development

### Building from Source

```bash
git clone https://github.com/phodal/acp-manager.git
cd acp-manager
./gradlew buildPlugin
```

The plugin will be built to `build/distributions/acp-manager-*.zip`

### Running in Development

```bash
./gradlew runIde
```

### Project Structure

```
acp-manager/
├── src/main/kotlin/com/github/phodal/acpmanager/
│   ├── acp/                 # Core ACP protocol implementation
│   │   ├── AcpClient.kt
│   │   ├── AcpProcessManager.kt
│   │   ├── AcpSessionManager.kt
│   │   └── AcpClientSessionOps.kt
│   ├── config/              # Configuration management
│   │   ├── AcpConfigService.kt
│   │   └── AcpAgentConfig.kt
│   └── ui/                  # User interface
│       ├── AcpToolWindowFactory.kt
│       ├── AcpManagerPanel.kt
│       ├── ChatPanel.kt
│       ├── MessagePanel.kt
│       ├── ChatInputToolbar.kt
│       └── AgentConfigDialog.kt
└── src/main/resources/META-INF/
    └── plugin.xml
```

## Troubleshooting

### Agent Won't Connect

1. Check that the agent command is in your PATH
2. Verify environment variables in config.yaml
3. Check `~/.acp-manager/logs/` for ACP logs
4. Try running the agent command manually to test

### Permission Errors

- Set `autoApprove: true` in config for trusted agents
- Or approve each request manually via the permission dialog

### Process Issues

- Processes are reused across sessions for performance
- Use "Disconnect" to cleanly stop an agent
- Restart IDE if processes become stuck

## Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## License

[License details here]

## Links

- [Agent Client Protocol Specification](https://github.com/agentclientprotocol/acp)
- [Issue Tracker](https://github.com/phodal/acp-manager/issues)
- [Plugin Page](https://plugins.jetbrains.com/plugin/acp-manager) (coming soon)
