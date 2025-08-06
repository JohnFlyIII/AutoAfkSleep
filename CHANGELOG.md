# Changelog

## [1.0.1] - 2025-08-06

### Fixed
- Fixed chat message processing - now properly detects both player and server messages
- Fixed disconnect phrase detection - players can now test by typing the phrase themselves
- Fixed auto-response triggers - now responds to messages containing sleep/AFK keywords

### Changed
- Removed auto-disable on shutdown feature - mod settings now persist between sessions
- Simplified message processing logic - only ignores the mod's own auto-response messages
- Improved auto-response detection for various chat formats

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