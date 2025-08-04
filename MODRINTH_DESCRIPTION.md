# AutoAFK Sleep - Automatic Bed Usage for AFK Players

Never worry about surviving the night while AFK again! This client-side mod automatically uses nearby beds when night falls, keeping you safe while you're away from your keyboard.

## ✨ Key Features

**🛏️ Smart Auto-Sleep System**
- Automatically detects nighttime and attempts to sleep
- Must be within 2 blocks of a bed (Minecraft's interaction range)
- Only works in the Overworld for safety (beds explode in other dimensions!)
- Intelligent timing system that checks more frequently as night approaches

**💬 Chat Monitoring & Auto-Response**
- Responds to direct messages and @mentions while you're AFK
- Fully customizable response message
- Built-in 30-second cooldown to prevent spam
- Automatically informs players how to make you disconnect if needed

**🚪 Remote Disconnect Feature**
- Set a custom phrase that allows trusted players to disconnect you
- Works with ANY chat message type (public, private, or even your own!)
- Perfect for server admins or friends who need to reach you urgently
- Default phrase: "afk-logout" (fully customizable)

**⚙️ Flexible Failure Handling**
When unable to sleep, choose your preferred action:
- **No Action** - Simply logs the failure
- **Auto-Disconnect** - Safely disconnects from the server
- **Custom Command** - Executes any command (e.g., `/spawn`, `/home`, `/warp afkspot`)

**🛡️ Safety First Design**
- Mod always starts DISABLED when launching the game
- Prevents accidental disconnects if you load into a world at night
- Simply enable with `/autoafksleep enable` or press K when ready

## 📋 Requirements

- **Minecraft**: 1.21.8+
- **Fabric Loader**: 0.16.0+
- **Fabric API**: Required (install separately)
- **Environment**: Client-side only (no server installation needed)

## 🎮 Quick Start

1. Install the mod with Fabric API
2. Join your world/server
3. Press **K** to open settings or use `/autoafksleep enable`
4. Place a bed within 2 blocks of your AFK spot
5. Configure your preferences and go AFK with confidence!

## 🔧 Commands

- `/autoafksleep enable` - Enable the mod
- `/autoafksleep disable` - Disable the mod  
- `/autoafksleep status` - Check current status
- `/autoafksleep ui` - Open configuration GUI
- `/autoafksleep help` - View all commands

## 🤝 Compatibility

This mod is client-side only and works on:
- Single-player worlds
- Multiplayer servers (no server-side installation required)
- Realms (where you have permission to sleep)
- Works alongside other mods

## 📝 License

Licensed under Apache 2.0 - You're free to use, modify, and redistribute with attribution.

## 🙏 Acknowledgments

Special thanks to the players at MCVerse City for testing and providing valuable feedback during development!

---

*Perfect for AFK farms, long building sessions, or any time you need to step away from the game! Stay safe from mobs while maintaining your server presence.*