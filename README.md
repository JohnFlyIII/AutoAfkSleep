# AutoAFK Sleep

A client-side Fabric mod that automatically uses beds at night when you're AFK, with chat monitoring and customizable failure handling.

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.8-brightgreen)
![Fabric](https://img.shields.io/badge/Fabric-0.130.0-blue)
![Environment](https://img.shields.io/badge/Environment-Client-yellow)

## Features

### Automatic Sleeping
- Automatically attempts to sleep when night falls
- Must be within 2 blocks of a bed
- Smart timing system that checks more frequently as night approaches
- Works in Overworld only (beds explode in Nether/End!)

### Chat Monitoring & Auto-Response
- Responds to direct messages and @mentions
- Customizable response message
- 30-second cooldown to prevent spam
- Sends instructions for the disconnect phrase when enabled

### Disconnect Phrase
- Set a custom phrase that will disconnect you from the server
- Works in ANY chat message (public, private, or even your own!)
- Choose a unique phrase to avoid accidental disconnects
- Default phrase: "afk-logout"

### Sleep Failure Actions
Choose what happens when unable to sleep:
- **No Action**: Just logs the failure
- **Disconnect**: Disconnects from the server
- **Custom Command**: Runs your specified command (e.g., `/spawn` or `/home`)

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft 1.21.8
2. Install [Fabric API](https://modrinth.com/mod/fabric-api)
3. Download AutoAFK Sleep from [Modrinth](https://modrinth.com/mod/autoafksleep)
4. Place the mod file in your `.minecraft/mods` folder
5. Launch Minecraft with the Fabric profile

## Usage

### Commands
- `/autoafksleep enable` - Enable the mod
- `/autoafksleep disable` - Disable the mod
- `/autoafksleep toggle` - Toggle mod on/off
- `/autoafksleep status` - Check current status
- `/autoafksleep ui` - Open configuration GUI
- `/autoafksleep help` - Show all commands

### Keybind
- Press **K** (default) to open the configuration screen
- Can be changed in Minecraft's Controls settings

### Configuration Options

#### Main Settings
- **Mod Enabled**: Toggle the entire mod on/off
- **Sleep Failure Action**: What to do when unable to sleep
- **Custom Command**: Command to run if sleep failure action is set to "Custom Command"

#### Auto-Response Settings
- **Auto Respond**: Enable/disable automatic chat responses
- **Response Message**: Custom message sent when someone messages you
- **Disconnect Phrase**: Enable/disable the disconnect phrase feature
- **Disconnect Phrase Text**: The phrase that triggers disconnection

## How It Works

1. **Night Detection**: The mod monitors the time of day and activates when night falls (time 12541-23458)
2. **Bed Search**: Looks for beds within 2 blocks of your position
3. **Sleep Attempt**: Automatically right-clicks the nearest bed
4. **Failure Handling**: If unable to sleep, performs your configured action

## Tips

- Keep a bed within 2 blocks of your AFK spot
- Choose a unique disconnect phrase to avoid accidental triggers
- Test your custom commands before going AFK
- The mod only works in single-player or on servers where you have permission to sleep

## Compatibility

- **Minecraft**: 1.21.8+
- **Fabric Loader**: 0.16.0+
- **Fabric API**: Required
- **ModMenu**: Recommended (for easy config access)
- **Environment**: Client-side only

## License

This mod is licensed under the Apache License 2.0. See [LICENSE](LICENSE) file for details.

When using or modifying this mod, you must:
- Give credit to the original author (John Fly III)
- State any changes you made
- Include the Apache 2.0 license

## Support

For issues, suggestions, or contributions:
- GitHub: [AutoAfkSleep](https://github.com/JohnFlyIII/AutoAfkSleep)
- Report bugs on the [Issues](https://github.com/JohnFlyIII/AutoAfkSleep/issues) page

## Credits

- Developed by John Fly
- Thanks to the Fabric community for their excellent documentation
- Special thanks to the players at [MCVerse City](https://mcverse.city/) for testing and feedback
- Special thanks to all beta testers and contributors

---

**Note**: This is a client-side mod. It will not affect other players and only works when you have permission to use beds on the server.