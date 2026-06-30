[plugin-README.md](https://github.com/user-attachments/files/29516911/plugin-README.md)
# DiscordBridge Plugin

A Paper plugin that bridges a Minecraft server with a Discord bot, forwarding chat messages and server events (deaths, achievements, commands) over HTTP, and broadcasting Discord messages back in-game.

## Features

- Forwards in-game chat to Discord
- Broadcasts Discord messages in-game
- Sends server event logs to Discord:
  -  Player death messages
  -  Player achievements
  -  Commands issued by players
- Exposes a local HTTP API for player list / location lookups (used by `/playerlist` and `/where` on the bot side)
- All outbound and inbound requests are authenticated with a shared token

## Architecture

This plugin must be paired with the [Minecraft Discord Bot](#) to function:

```
Minecraft Server (this Plugin)
        ↕ HTTP (authenticated via X-Auth-Token)
   Discord Bot
        ↕
     Discord Server
```

## Installation

### 1. Build the plugin

```bash
cd minecraft-plugin
mvn package
```

The compiled jar will be at `target/discordbridge-1.0.0.jar`.

### 2. Copy to your server's plugins folder

```bash
cp target/discordbridge-1.0.0.jar /path/to/your/server/plugins/
```

### 3. Configure

On first run, the plugin generates a default `config.yml` under `plugins/DiscordBridge/`. Edit it as follows:

```yaml
# Port this plugin's local HTTP API listens on
server-port: 8080

# URL of the Discord Bot's API
discord-bot-url: "http://localhost:3000"

# Shared secret token, must match BOT_API_TOKEN in the Discord Bot's .env
bot-api-token: "your-shared-secret-here"
```

| Field | Description |
|---|---|
| `server-port` | Port for this plugin's local API (used by the bot for `/playerlist` and `/where`) |
| `discord-bot-url` | The address of the Discord Bot, used for sending chat/event data |
| `bot-api-token` | Authentication token shared with the Discord Bot; requests without a matching token will be rejected |

### 4. Restart the server

A full restart is required (not `/reload`) for the plugin to load correctly.

## Companion Discord Bot

This plugin requires the [Minecraft Discord Bot](#) running and reachable at the URL configured in `discord-bot-url`. See that project's README for bot-side setup.

## License

MIT
