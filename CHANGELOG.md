# Changelog

## [1.0.0] - 2025-08-04

### Initial Release

#### Features
- **Automatic Sleeping**: Automatically uses beds when night falls if within 2 blocks
- **Smart Timing**: Checks more frequently as night approaches to save resources
- **Chat Monitoring**: Responds to direct messages and @mentions with customizable messages
- **Disconnect Phrase**: Set a custom phrase that disconnects you from the server
- **Failure Actions**: Choose what happens when unable to sleep (disconnect, custom command, or nothing)
- **Configuration GUI**: Easy-to-use settings screen accessible via keybind (default: K)
- **Commands**: Full command system for all features (`/autoafksleep help`)
- **ModMenu Integration**: Access settings through ModMenu if installed
- **Safety Start**: Mod always starts disabled to prevent accidental disconnects on world load

#### Technical Details
- Client-side only mod
- Supports Minecraft 1.21.8+
- Requires Fabric API
- Compatible with other mods
- Lightweight and efficient

#### Known Limitations
- Only works in the Overworld (beds explode in Nether/End)
- Requires being within 2 blocks of a bed
- Sleep may fail on some servers with custom sleep mechanics

#### Acknowledgments
- Special thanks to the players at MCVerse City (https://mcverse.city/) for testing and providing valuable feedback during development