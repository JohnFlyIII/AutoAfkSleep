# Changelog

## [1.2.1] - 2025-08-15

### Fixed
- **AutoEat Slot Selection**: Fixed critical issue where food wouldn't be selected properly
  - Added Mixin accessor to properly access and modify the selected hotbar slot
  - Now correctly switches to food slot before attempting to eat
  - Synchronizes slot changes with server using packets
- **Log Spam**: Removed excessive logging from overlay messages (coordinates, etc.)
- **Code Quality**: Removed all blocking operations, improved state management

### Technical
- Implemented Mixin system for accessing private PlayerInventory.selectedSlot field
- Simplified slot switching logic - direct modification instead of complex workarounds
- Better error handling and state validation

## [1.2.0] - 2025-08-15

### Added
- **AutoEat Feature**: Automatically eats food when hunger drops below threshold
  - Prioritizes non-magical food (bread, meat, etc.) over magical food (golden apples, etc.)
  - Avoids poisonous food (rotten flesh, spider eyes, poisonous potatoes, etc.)
  - Configurable hunger threshold (default: 14/20)
  - Option to eat stews/soups (default: enabled)
  - Minimum food value filter (default: 2)
  - Auto-disconnect when out of safe food and critically hungry
- **AutoEat Configuration**:
  - GUI controls in config screen
  - Commands: `/autoafksleep config autoEat`, `autoEatThreshold`, etc.
  - Persistent settings in config file

### Changed
- Updated mod description to include AutoEat functionality
- Enhanced status command to show AutoEat status

### Technical
- Non-blocking food consumption with proper timing (1.6 seconds)
- Smart food selection algorithm prioritizing safety and efficiency
- Integrated with existing mod enable/disable system

## [1.1.0] - 2025-08-12

### Performance Improvements
- **Intelligent Scheduling**: Now calculates exact time until night instead of constant polling
- **Reduced Server Load**: Polling frequency reduced by 6x during night (5s → 30s)
- **Resource Efficiency**: During day, waits up to 10 minutes without any CPU usage
- **Optimized Bed Finding**: Uses BlockPos.Mutable and early distance checks for better performance

### Code Quality
- **Fixed Critical Bug**: Removed Thread.sleep() from event handlers (was blocking game thread!)
- **Non-blocking Operations**: Replaced with CompletableFuture for async message sending
- **Better Modularity**: Extracted time calculations and bed finding into dedicated methods
- **Pattern Matching**: Pre-compiled regex and HashSet lookups for faster message processing

### New Features
- **Configurable Timing**: All timing parameters now adjustable in config file
- **Failure Backoff**: Stops attempting after configurable consecutive failures
- **Dimension Caching**: 5-second cache for dimension checks to reduce overhead
- **Smart Recovery**: Resets failure counter when day arrives

### Changed
- Wake-up margin before night: configurable (default 30s)
- Check interval during night: configurable (default 30s)
- Check interval after failures: configurable (default 60s)
- Maximum consecutive failures: configurable (default 3)
- Chat response cooldown: configurable (default 30s)
- Sleep attempt cooldown: configurable (default 3s)

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