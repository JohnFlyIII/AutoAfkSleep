# Modrinth Submission Guide for AutoAFK Sleep

## Mod Information

**Name**: AutoAFK Sleep  
**Version**: 1.0.0  
**Game Version**: 1.21.8  
**Loader**: Fabric  
**Environment**: Client  
**License**: Apache-2.0  

## Files to Upload

1. **Primary File**: `build/libs/autoafksleep-1.0.0.jar`
2. **Sources**: `build/libs/autoafksleep-1.0.0-sources.jar` (optional)

## Modrinth Page Content

### Short Description (150 chars max)
"Automatically sleep when AFK! Features chat monitoring, disconnect phrases, and customizable failure actions. Perfect for AFK farms and idle players."

### Categories
- Utility
- Adventure
- Optimization

### Gallery Images Needed
1. Screenshot of config screen
2. Screenshot of instructions screen
3. Screenshot showing auto-sleep in action
4. Screenshot of chat response feature

### Dependencies
- **Required**: Fabric API
- **Optional**: ModMenu (for easy config access)

### Supported Versions
- Minecraft: 1.21.8+ (may work on 1.21.x)
- Fabric Loader: 0.16.0+

## Features to Highlight

1. **Smart Auto-Sleep System**
   - Automatically detects night time
   - Finds and uses beds within 2 blocks
   - Efficient checking algorithm

2. **Chat Integration**
   - Auto-responds to direct messages
   - Customizable response messages
   - 30-second cooldown to prevent spam

3. **Safety Features**
   - Disconnect phrase for emergencies
   - Configurable failure actions
   - Works only in Overworld (safety check)

4. **User-Friendly**
   - GUI configuration screen
   - Keybind support (default: K)
   - Full command system
   - ModMenu integration

## Version Changelog

### 1.0.0 - Initial Release
- Automatic sleeping when night falls
- Chat monitoring with auto-response
- Disconnect phrase detection
- Configurable failure actions
- Configuration GUI
- Command system
- ModMenu integration

## Links to Include
- **Source Code**: https://github.com/JohnFlyIII/AutoAfkSleep
- **Issue Tracker**: https://github.com/JohnFlyIII/AutoAfkSleep/issues
- **Wiki**: (Create if needed)

## Tags
- afk
- auto-sleep
- automation
- client-side
- utility
- quality-of-life
- fabric

## Additional Notes

1. This is a CLIENT-SIDE mod - emphasize this in the description
2. Works on servers but respects server sleep rules
3. No server installation required
4. Compatible with most other mods

## Pre-Submission Checklist

- [x] Code reviewed and documented
- [x] README.md created with full documentation
- [x] LICENSE file (MIT) included
- [x] fabric.mod.json properly configured
- [x] Mod builds successfully
- [x] Changelog prepared
- [x] .gitignore created
- [ ] Test mod in clean Minecraft instance
- [ ] Take screenshots for gallery
- [ ] Create mod icon if needed (currently using default)