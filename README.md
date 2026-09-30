# Netherite Finder — Minecraft 26.1.1 Fabric

Press **N** to scan blocks currently loaded around you for the nearest `minecraft:netherite_block`.

### Version 1.1 features
- Searches a 64-block radius.
- Shows the nearest block's coordinates and distance in chat.
- Automatically refreshes the target every 10 ticks while a target exists.
- Draws a cyan outline around the selected netherite block.
- Draws a HUD arrow near the crosshair pointing toward the selected block.
- Displays the target distance below the arrow.
- Does not request or read unloaded chunks.

### Build requirements
Minecraft 26.1.1, Fabric Loader 0.18.4+, Fabric API, Java 25, Gradle 9.4+.

Build with:

```text
./gradlew build
```

The built JAR will be in `build/libs/`.
