package dev.voicegroupmanager;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static dev.voicegroupmanager.VoiceGroupManagerPlugin.GROUP_TYPES;

/**
 * Defines every command as a Brigadier tree, so players get live syntax hints, typed arguments and
 * tab completion straight from the client. Commands a player may not use are hidden from them.
 */
final class CommandTree {
    private static final String FORCEJOIN_USAGE = "/forcejoingroup <player> <group name or ID>";
    private static final String ADD_USAGE = "/persistentgroup add <name> <normal|open|isolated> <true|false> [password]";

    private final GroupStore store;
    private final CommandActions actions;

    CommandTree(GroupStore store, CommandActions actions) {
        this.store = store;
        this.actions = actions;
    }

    void register(Commands registrar) {
        registrar.register(instantGroup(), "Create or join an instant voice group with nearby players", List.of());
        registrar.register(persistentGroup(), "Manage persistent voice groups", List.of());
        registrar.register(autoJoinGroup(), "Configure personal or global automatic group joining", List.of());
        registrar.register(forceJoinGroup(), "Move a player into your current voice group", List.of());
        registrar.register(manage(), "Reload the plugin or see its status and the voice groups in use", List.of("vgm"));
    }

    // ---- /voicegroupmanager ----

    private LiteralCommandNode<CommandSourceStack> manage() {
        return Commands.literal("voicegroupmanager")
                .requires(s -> can(s, Permissions.RELOAD) || can(s, Permissions.INFO))
                .executes(usage("/voicegroupmanager <reload|info|groups>"))
                .then(Commands.literal("reload")
                        .requires(s -> can(s, Permissions.RELOAD))
                        .executes(run(actions::reload)))
                .then(Commands.literal("info")
                        .requires(s -> can(s, Permissions.INFO))
                        .executes(run(actions::info)))
                .then(Commands.literal("groups")
                        .requires(s -> can(s, Permissions.INFO))
                        .executes(run(actions::groups)))
                .build();
    }

    // ---- /instantgroup ----

    private LiteralCommandNode<CommandSourceStack> instantGroup() {
        return Commands.literal("instantgroup")
                .requires(s -> can(s, Permissions.INSTANT))
                .executes(run(sender -> actions.instant(sender, null)))
                .then(Commands.argument("range", DoubleArgumentType.doubleArg(1))
                        .suggests((ctx, builder) -> suggest(builder, actions.rangeSuggestions()))
                        .executes(ctx -> {
                            actions.instant(sender(ctx), DoubleArgumentType.getDouble(ctx, "range"));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    // ---- /persistentgroup ----

    private LiteralCommandNode<CommandSourceStack> persistentGroup() {
        return Commands.literal("persistentgroup")
                .requires(s -> can(s, Permissions.PERSISTENT))
                .executes(usage("/persistentgroup <list|add|edit|remove>"))
                .then(Commands.literal("list").executes(run(actions::listGroups)))
                .then(addNode())
                .then(editNode())
                .then(Commands.literal("remove")
                        .then(Commands.argument("group", StringArgumentType.greedyString())
                                .suggests((ctx, builder) -> suggest(builder, store.names()))
                                .executes(ctx -> {
                                    actions.removeGroup(sender(ctx), StringArgumentType.getString(ctx, "group"));
                                    return Command.SINGLE_SUCCESS;
                                })))
                .build();
    }

    /**
     * add &lt;name&gt; &lt;normal|open|isolated&gt; &lt;true|false&gt; [password]
     *
     * <p>Type and hidden are required, in that order, so the password can be any text. Stopping early
     * shows the usage line instead of guessing.
     */
    private LiteralArgumentBuilder<CommandSourceStack> addNode() {
        RequiredArgumentBuilder<CommandSourceStack, String> name = Commands.argument("name", StringArgumentType.string())
                .executes(usage(ADD_USAGE));
        for (String type : GROUP_TYPES) {
            LiteralArgumentBuilder<CommandSourceStack> typeNode = Commands.literal(type).executes(usage(ADD_USAGE));
            typeNode.then(Commands.argument("hidden", BoolArgumentType.bool())
                    .executes(ctx -> add(ctx, type, null))
                    .then(Commands.argument("password", StringArgumentType.greedyString())
                            .executes(ctx -> add(ctx, type,
                                    cleanValue(StringArgumentType.getString(ctx, "password"))))));
            name.then(typeNode);
        }
        return Commands.literal("add").executes(usage(ADD_USAGE)).then(name);
    }

    private int add(CommandContext<CommandSourceStack> ctx, String type, String password) {
        actions.addGroup(sender(ctx), StringArgumentType.getString(ctx, "name"), type,
                BoolArgumentType.getBool(ctx, "hidden"), password);
        return Command.SINGLE_SUCCESS;
    }

    /** edit &lt;group&gt; &lt;name|type|hidden|password&gt; &lt;value&gt; */
    private LiteralArgumentBuilder<CommandSourceStack> editNode() {
        LiteralArgumentBuilder<CommandSourceStack> type = Commands.literal("type");
        for (String groupType : GROUP_TYPES) {
            type.then(Commands.literal(groupType).executes(ctx -> edit(ctx, "type", groupType)));
        }
        return Commands.literal("edit")
                .then(Commands.argument("group", StringArgumentType.string())
                        .suggests((ctx, builder) -> suggest(builder, store.names()))
                        .then(Commands.literal("name")
                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                        .executes(ctx -> edit(ctx, "name",
                                                cleanValue(StringArgumentType.getString(ctx, "value"))))))
                        .then(type)
                        .then(Commands.literal("hidden")
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> edit(ctx, "hidden",
                                                Boolean.toString(BoolArgumentType.getBool(ctx, "value"))))))
                        .then(Commands.literal("password")
                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                        .suggests((ctx, builder) -> suggest(builder, List.of("none")))
                                        .executes(ctx -> edit(ctx, "password",
                                                cleanValue(StringArgumentType.getString(ctx, "value")))))));
    }

    private int edit(CommandContext<CommandSourceStack> ctx, String property, String value) {
        actions.editGroup(sender(ctx), StringArgumentType.getString(ctx, "group"), property, value);
        return Command.SINGLE_SUCCESS;
    }

    // ---- /autojoingroup ----

    private LiteralCommandNode<CommandSourceStack> autoJoinGroup() {
        return Commands.literal("autojoingroup")
                .requires(s -> can(s, Permissions.AUTOJOIN) || can(s, Permissions.AUTOJOIN_GLOBAL))
                .executes(usage("/autojoingroup <set <group> [password]|remove|global>"))
                .then(Commands.literal("set")
                        .requires(s -> can(s, Permissions.AUTOJOIN))
                        .then(Commands.argument("group", StringArgumentType.string())
                                .suggests((ctx, builder) -> suggest(builder, store.names()))
                                .executes(ctx -> {
                                    actions.autoJoinSet(sender(ctx), StringArgumentType.getString(ctx, "group"), null);
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(Commands.argument("password", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            actions.autoJoinSet(sender(ctx), StringArgumentType.getString(ctx, "group"),
                                                    cleanValue(StringArgumentType.getString(ctx, "password")));
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                .then(Commands.literal("remove")
                        .requires(s -> can(s, Permissions.AUTOJOIN))
                        .executes(run(actions::autoJoinRemove)))
                .then(Commands.literal("global")
                        .requires(s -> can(s, Permissions.AUTOJOIN_GLOBAL))
                        .executes(usage("/autojoingroup global <set <group>|remove|force <true|false>>"))
                        .then(Commands.literal("set")
                                .then(Commands.argument("group", StringArgumentType.greedyString())
                                        .suggests((ctx, builder) -> suggest(builder, store.names()))
                                        .executes(ctx -> {
                                            actions.autoJoinGlobalSet(sender(ctx), StringArgumentType.getString(ctx, "group"));
                                            return Command.SINGLE_SUCCESS;
                                        })))
                        .then(Commands.literal("remove").executes(run(actions::autoJoinGlobalRemove)))
                        .then(Commands.literal("force")
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            actions.autoJoinGlobalForce(sender(ctx), BoolArgumentType.getBool(ctx, "value"));
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                .build();
    }

    // ---- /forcejoingroup ----

    private LiteralCommandNode<CommandSourceStack> forceJoinGroup() {
        return Commands.literal("forcejoingroup")
                .requires(s -> can(s, Permissions.FORCEJOIN))
                .executes(usage(FORCEJOIN_USAGE))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggest(builder, actions.onlinePlayerNames()))
                        .executes(usage(FORCEJOIN_USAGE))
                        .then(Commands.argument("group", StringArgumentType.greedyString())
                                .suggests((ctx, builder) -> suggest(builder, actions.forceJoinGroupSuggestions()))
                                .executes(ctx -> {
                                    actions.forceJoin(sender(ctx), StringArgumentType.getString(ctx, "player"),
                                            StringArgumentType.getString(ctx, "group"));
                                    return Command.SINGLE_SUCCESS;
                                })))
                .build();
    }

    // ---- Helpers ----

    private static CommandSender sender(CommandContext<CommandSourceStack> ctx) {
        return ctx.getSource().getSender();
    }

    private static boolean can(CommandSourceStack source, String permission) {
        return source.getSender().hasPermission(permission);
    }

    private static Command<CommandSourceStack> run(Consumer<CommandSender> action) {
        return ctx -> {
            action.accept(sender(ctx));
            return Command.SINGLE_SUCCESS;
        };
    }

    private Command<CommandSourceStack> usage(String text) {
        return run(sender -> actions.usage(sender, text));
    }

    /** Greedy text may still contain "quoted" parts; drop the quotes the same way for every command. */
    private static String cleanValue(String raw) {
        return ArgParser.join(ArgParser.tokenize(raw), 0);
    }

    private static CompletableFuture<Suggestions> suggest(SuggestionsBuilder builder, Collection<String> options) {
        String start = builder.getRemainingLowerCase();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(start)) builder.suggest(option);
        }
        return builder.buildFuture();
    }
}
