package io.github.mshortensia.onjoincommands;

import io.github.mshortensia.onjoincommands.mixin.ClientPacketListenerAccessor;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.impl.command.client.ClientCommandInternals;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class OnJoinCommandsClient implements ClientModInitializer {

    public static final String MOD_ID = "on-join-commands";

    private static Path configPath;

    private static final List<CommandEntry> commands = new ArrayList<>();

    private static int commandIndex = 0;

    private static long delay = 500;

    private static long nextExecutionTime = 0;

    private static boolean executing = false;

    private static boolean joined = false;


    @Override
    public void onInitializeClient() {

        /*
         * Create the config as soon as Minecraft starts.
         */
        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            if (configPath == null) {

                configPath = client.gameDirectory
                        .toPath()
                        .resolve("config")
                        .resolve("on-join-commands.cfg");

                createConfig();
            }

            tick(client);
        });


        /*
         * World/server join.
         */
        ClientPlayConnectionEvents.JOIN.register(
                (handler, sender, client) -> {

                    System.out.println(
                            "[" + MOD_ID + "] Joined world/server."
                    );

                    joined = true;

                    start(client);
                }
        );


        /*
         * World/server disconnect.
         */
        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> {

                    System.out.println(
                            "[" + MOD_ID + "] Disconnected."
                    );

                    joined = false;

                    stop();
                }
        );
    }


    /*
     * ============================================================
     * TICK
     * ============================================================
     */

    private static void tick(Minecraft client) {

        if (!executing) {
            return;
        }

        if (!joined) {
            stop();
            return;
        }

        if (client.player == null) {
            return;
        }

        if (client.getConnection() == null) {
            return;
        }

        long now = System.currentTimeMillis();

        if (now < nextExecutionTime) {
            return;
        }

        if (commandIndex >= commands.size()) {

            System.out.println(
                    "[" + MOD_ID + "] Finished commands."
            );

            stop();

            return;
        }

        CommandEntry entry =
                commands.get(commandIndex);

        commandIndex++;

        /*
         * Schedule the next command.
         */
        nextExecutionTime = now + delay;

        executeCommand(client, entry);
    }


    /*
     * ============================================================
     * START
     * ============================================================
     */

    private static void start(Minecraft client) {

        stop();

        Config config = loadConfig();

        if (config == null) {
            return;
        }

        if (!config.enabled) {

            System.out.println(
                    "[" + MOD_ID + "] Disabled in config."
            );

            return;
        }

        if (config.commands.isEmpty()) {

            System.out.println(
                    "[" + MOD_ID + "] No commands configured."
            );

            return;
        }

        commands.addAll(config.commands);

        delay = Math.max(0, config.delay);

        commandIndex = 0;

        nextExecutionTime =
                System.currentTimeMillis();

        executing = true;

        System.out.println(
                "[" + MOD_ID
                        + "] Starting "
                        + commands.size()
                        + " command(s)."
        );
    }


    /*
     * ============================================================
     * STOP
     * ============================================================
     */

    private static void stop() {

        commands.clear();

        commandIndex = 0;

        nextExecutionTime = 0;

        executing = false;
    }


    /*
     * ============================================================
     * EXECUTE
     * ============================================================
     */

    private static void executeCommand(
            Minecraft client,
            CommandEntry entry
    ) {

        String command = entry.command.trim();

        if (command.isEmpty()) {
            return;
        }

        /*
         * Allow both:
         *
         * client:/foo
         * client:foo
         *
         * and the same for server commands.
         */
        if (command.startsWith("/")) {
            command = command.substring(1);
        }

        if (command.isEmpty()) {
            return;
        }

        if (entry.type == CommandType.SERVER) {

            executeServerCommand(
                    client,
                    command
            );

            return;
        }

        if (entry.type == CommandType.CLIENT) {

            executeClientCommand(
                    client,
                    command
            );
        }
    }


    /*
     * ============================================================
     * SERVER COMMAND
     * ============================================================
     */

    private static void executeServerCommand(
            Minecraft client,
            String command
    ) {

        ClientPacketListener connection =
                client.getConnection();

        if (connection == null) {

            System.err.println(
                    "[" + MOD_ID
                            + "] Cannot send server command: "
                            + "not connected."
            );

            return;
        }

        try {

            connection.sendCommand(command);

            System.out.println(
                    "[" + MOD_ID
                            + "] Server command: /"
                            + command
            );

        } catch (Exception e) {

            System.err.println(
                    "[" + MOD_ID
                            + "] Failed server command: /"
                            + command
            );

            e.printStackTrace();
        }
    }


    /*
     * ============================================================
     * CLIENT COMMAND
     * ============================================================
     *
     * This uses Fabric's actual ClientSuggestionProvider.
     *
     * Fabric's own ClientPacketListenerMixin does essentially
     * the same thing when intercepting client commands.
     */

    private static void executeClientCommand(
            Minecraft client,
            String command
    ) {

        ClientPacketListener connection =
                client.getConnection();

        if (connection == null) {

            System.err.println(
                    "[" + MOD_ID
                            + "] Cannot execute client command: "
                            + "not connected."
            );

            return;
        }

        try {

            /*
             * Obtain Fabric/Minecraft's real client command source.
             */
            ClientSuggestionProvider suggestionsProvider =
                    ((ClientPacketListenerAccessor) connection)
                            .onjoinCommands$getSuggestionsProvider();

            if (suggestionsProvider == null) {

                System.err.println(
                        "[" + MOD_ID
                                + "] Client command source "
                                + "is not available yet."
                );

                /*
                 * Do not silently lose the command.
                 *
                 * Put it back at the current position so the next
                 * tick can try again.
                 */
                commandIndex--;

                nextExecutionTime =
                        System.currentTimeMillis() + 100;

                return;
            }

            FabricClientCommandSource source =
                    (FabricClientCommandSource) suggestionsProvider;

            /*
             * This is the same execution mechanism Fabric uses.
             *
             * The third argument is null for the normal client
             * command path, exactly like Fabric's sendCommand(String)
             * interception.
             */
            boolean handled =
                    ClientCommandInternals.executeCommand(
                            command,
                            source,
                            null
                    );

            if (handled) {

                System.out.println(
                        "[" + MOD_ID
                                + "] Client command: /"
                                + command
                );

            } else {

                System.err.println(
                        "[" + MOD_ID
                                + "] Unknown client command: /"
                                + command
                );
            }

        } catch (Exception e) {

            System.err.println(
                    "[" + MOD_ID
                            + "] Failed client command: /"
                            + command
            );

            e.printStackTrace();
        }
    }


    /*
     * ============================================================
     * CONFIG CREATION
     * ============================================================
     */

    private static void createConfig() {

        try {

            Files.createDirectories(
                    configPath.getParent()
            );

            if (Files.exists(configPath)) {
                return;
            }

            List<String> lines = List.of(

                    "# On Join Commands configuration",
                    "#",
                    "# Commands are executed when you",
                    "# join a Minecraft world/server.",
                    "#",
                    "# Format:",
                    "# client:/command",
                    "# server:/command",
                    "#",
                    "# Example if you write a client-side command from another Fabric mod (like Spunkyinsaan's Skin Changer):",
                    "# client:/skinc model slim",
                    "#",
                    "# Example if you write a server-side command:",
                    "# server:/say Hello",
                    "#",
                    "# Delay is milliseconds.",
                    "",
                    "enabled=true",
                    "delay=500",
                    "",
                    "# Put commands below (without '#' symbol):",
                    "",
                    "# client:/example",
                    "# server:/example"
            );

            Files.write(
                    configPath,
                    lines
            );

            System.out.println(
                    "[" + MOD_ID
                            + "] Created config: "
                            + configPath
            );

        } catch (IOException e) {

            System.err.println(
                    "[" + MOD_ID
                            + "] Could not create config."
            );

            e.printStackTrace();
        }
    }


    /*
     * ============================================================
     * CONFIG LOADING
     * ============================================================
     */

    private static Config loadConfig() {

        if (configPath == null) {
            return null;
        }

        try {

            if (!Files.exists(configPath)) {
                createConfig();
            }

            if (!Files.exists(configPath)) {
                return null;
            }

            List<String> lines =
                    Files.readAllLines(configPath);

            Config config = new Config();

            for (String raw : lines) {

                String line = raw.trim();

                if (line.isEmpty()) {
                    continue;
                }

                if (line.startsWith("#")) {
                    continue;
                }


                /*
                 * enabled=
                 */
                if (line.startsWith("enabled=")) {

                    String value =
                            line.substring(
                                    "enabled=".length()
                            ).trim();

                    config.enabled =
                            Boolean.parseBoolean(value);

                    continue;
                }


                /*
                 * delay=
                 */
                if (line.startsWith("delay=")) {

                    String value =
                            line.substring(
                                    "delay=".length()
                            ).trim();

                    try {

                        config.delay =
                                Long.parseLong(value);

                    } catch (NumberFormatException e) {

                        System.err.println(
                                "[" + MOD_ID
                                        + "] Invalid delay: "
                                        + value
                        );
                    }

                    continue;
                }


                /*
                 * client:
                 */
                if (startsWithIgnoreCase(
                        line,
                        "client:"
                )) {

                    config.commands.add(
                            new CommandEntry(
                                    CommandType.CLIENT,
                                    line.substring(
                                            "client:".length()
                                    ).trim()
                            )
                    );

                    continue;
                }


                /*
                 * server:
                 */
                if (startsWithIgnoreCase(
                        line,
                        "server:"
                )) {

                    config.commands.add(
                            new CommandEntry(
                                    CommandType.SERVER,
                                    line.substring(
                                            "server:".length()
                                    ).trim()
                            )
                    );
                }
            }

            return config;

        } catch (IOException e) {

            System.err.println(
                    "[" + MOD_ID
                            + "] Could not read config."
            );

            e.printStackTrace();

            return null;
        }
    }


    private static boolean startsWithIgnoreCase(
            String value,
            String prefix
    ) {

        return value.regionMatches(
                true,
                0,
                prefix,
                0,
                prefix.length()
        );
    }


    /*
     * ============================================================
     * DATA
     * ============================================================
     */

    private enum CommandType {
        CLIENT,
        SERVER
    }


    private static class CommandEntry {

        final CommandType type;

        final String command;

        CommandEntry(
                CommandType type,
                String command
        ) {

            this.type = type;
            this.command = command;
        }
    }


    private static class Config {

        boolean enabled = true;

        long delay = 500;

        List<CommandEntry> commands =
                new ArrayList<>();
    }
}
