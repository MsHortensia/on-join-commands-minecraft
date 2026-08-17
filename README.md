# On Join Commands

A Fabric Minecraft mod that automatically executes configured commands when joining a world or server.

## Requirements

- Minecraft 26.2
- Fabric Loader 0.19.3+
- Fabric API 0.157.0+
- Java 25

## Features

- Execute client commands on join
- Execute server commands on join
- Configurable delay between commands
- Simple config file

## Downloading

Go to the **Releases** section and pick the correct Minecraft version (for example, Minecraft 26.2), then download the mod `.jar` file.

## Installation

1. Install Fabric Loader
2. Install Fabric API
3. Put the mod `.jar` into your `mods` folder
4. Launch Minecraft once for the config file to be created, then close the game

## Configuration

The config file is located at:

.minecraft/config/on-join-commands.cfg

It should look like this:

```cfg
# On Join Commands configuration
#
# Commands are executed when you
# join a Minecraft world/server.
#
# Format:
# client:/command
# server:/command
#
# Example if you write a client-side command from another Fabric mod (like Spunkyinsaan's Skin Changer):
# client:/skinc model slim
#
# Example if you write a server-side command:
# server:/say Hello
#
# Delay is milliseconds.

enabled=true
delay=500

# Put commands below (without '#' symbol):

# client:/example
# server:/example
```
Follow the instructions in the config file to add your own commands.

## Checking the result

After writing your commands, launch Minecraft again and join a world or server (where you have the required permissions) and see if they are being applied.

Additionally, you can disable the commands by changing `enabled=true` to `enabled=false`, and also change the execution delay by modifying `delay=500` value.
