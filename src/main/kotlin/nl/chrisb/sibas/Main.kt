package nl.chrisb.sibas

import dev.minn.jda.ktx.*
import dev.minn.jda.ktx.interactions.*
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.events.guild.GuildReadyEvent
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.MessageUpdateEvent
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent
import java.time.ZoneOffset

// https://discord.com/oauth2/authorize?client_id=865179659591483403&scope=bot+applications.commands

fun main() {
    val jda = JDABuilder.createLight(
        System.getenv("DISCORD_TOKEN")
            ?: throw RuntimeException("Environment variable DISCORD_TOKEN should contain the discord bot token")
    )
        .injectKTX()
        .setActivity(Activity.playing("Tetris"))
        .build()

    jda.listener<MessageReactionAddEvent> { event ->
        event.retrieveMessage().queue {
            Messages.updateMessage(it)
        }
    }

    jda.listener<MessageReactionRemoveEvent> { event ->
        event.retrieveMessage().queue {
            Messages.updateMessage(it)
        }
    }

    jda.listener<MessageUpdateEvent> { event ->
        Messages.updateMessage(event.message)
    }

    jda.listener<GuildReadyEvent> {
        it.guild.updateCommands {
            addCommands(
                Command("index", "Index messages of channel") {
                    option<MessageChannel>("channel", "Channel to index", required = true)
                },
                Command("indexall", "Index messages of all channels"),
                Command("cleardb", "Clear the database"),
                Command("reindexall", "Clear the database and reindex all channels"),
                Command("stats", "Show bot stats"),
                Command("leaderboard", "Show various leaderboards") {
                    addSubcommandGroups(
                        SubcommandGroup("channel", "Channel related leaderboards") {
                            subcommand("messages", "Show a leaderboard of channels with the most messages")
                            subcommand("upvotes", "Show a leaderboard of channels with the most upvotes")
                        },
                        SubcommandGroup("user", "User related leaderboards") {
                            subcommand("messages", "Show a leaderboard of users with the most messages")
                            subcommand("upvotes", "Show a leaderboard of users with the most upvotes")
                        },
                        SubcommandGroup("message", "Message related leaderboards") {
                            subcommand("upvotes", "Show a leaderboard of messages with the most upvotes") {
                                option<MessageChannel>("channel", "Channel for the messages", required = false)
                            }
                        }
                    )
                },
            )

            queue()
        }
    }

    jda.onCommandHandleErrors("stats") {
        val stats = Messages.stats()

        it.replyEmbeds(Embed {
            field(name = "Messages", value = stats.messages.toString())
            field(name = "Reactions", value = stats.reactions.toString())
        }).queue()
    }

    jda.onCommandHandleErrors("index") { event ->
        val channel = event.getOptionsByName("channel")[0].asMessageChannel!!
        event.reply("Indexing <#${channel.id}>...").queue()

        Messages.index(channel, event).thenAccept {
            event.hook.editOriginal("**DONE!** Indexed <#${channel.id}>. _($it messages)_").queue()
        }
    }

    jda.onCommandHandleErrors("indexall") { event ->
        event.reply("Indexing all channels...").queue()

        var count = 0
        event.guild?.textChannels?.forEach {
            count += Messages.index(it, event).await()
        }

        event.hook.editOriginal("**DONE!** Indexed all channels. _($count messages)_").queue()
    }

    jda.onCommandHandleErrors("cleardb") { event ->
        if (event.member?.isAdmin() != true) {
            event.reply("**ERROR!** You are not allowed to do this.")
                .setEphemeral(true)
                .queue()
        } else {
            event.reply("Clearing the database...").queue()
            Messages.clearDB()
            event.hook.editOriginal("**DONE!** Cleared the database.").queue()
        }
    }

    jda.onCommandHandleErrors("reindexall") { event ->
        if (event.member?.isAdmin() != true) {
            event.reply("**ERROR!** You are not allowed to do this.")
                .setEphemeral(true)
                .queue()
        } else {
            event.reply("Clearing the database...").queue()
            Messages.clearDB()
            event.hook.editOriginal("Indexing all channels...").queue()

            var count = 0
            event.guild?.textChannels?.forEach {
                count += Messages.index(it, event).await()
            }

            event.hook.editOriginal("**DONE!** Indexed all channels. _($count messages)_").queue()
        }
    }

    jda.onCommandHandleErrors("leaderboard") { event ->
        event.reply("Indexing all channels...").queue()

        var count = 0
        event.guild?.textChannels?.forEach {
            count += Messages.index(it, event).await()
        }

        when (event.subcommandGroup) {
            "channel" -> when (event.subcommandName) {
                "messages" -> {
                    val leaderboard = Messages.channelMessagesLeaderboard()

                    event.hook.editOriginal("Indexed all channels. _($count messages)_")
                        .and(event.hook.editOriginalEmbeds(Embed {
                            title = "Most messages in channels"
                            description = leaderboard.joinToString("\n") {
                                "<#${it.first}>: ${it.second} messages"
                            }
                        })).queue()
                }
                "upvotes" -> {
                    val leaderboard = Messages.channelUpvotesLeaderboard()

                    event.hook.editOriginal("Indexed all channels. _($count messages)_")
                        .and(event.hook.editOriginalEmbeds(Embed {
                            title = "Most upvoted channels"
                            description = leaderboard.joinToString("\n") {
                                "<#${it.first}>: ${it.second} upvotes"
                            }
                        })).queue()
                }
            }
            "user" -> when (event.subcommandName) {
                "messages" -> {
                    val leaderboard = Messages.userMessagesLeaderboard()

                    event.hook.editOriginal("Indexed all channels. _($count messages)_")
                        .and(event.hook.editOriginalEmbeds(Embed {
                            title = "Most messages by users"
                            description = leaderboard.joinToString("\n") {
                                "<@${it.first}>: ${it.second} messages"
                            }
                        })).queue()
                }
                "upvotes" -> {
                    val leaderboard = Messages.userUpvoteLeaderboard()

                    event.hook.editOriginal("Indexed all channels. _($count messages)_")
                        .and(event.hook.editOriginalEmbeds(Embed {
                            title = "Most upvoted users"
                            description = leaderboard.joinToString("\n") {
                                "<@${it.first}>: ${it.second} upvotes"
                            }
                        })).queue()
                }
            }
            "message" -> when (event.subcommandName) {
                "upvotes" -> {
                    val channel = event.getOption("channel")?.asMessageChannel
                    val leaderboard = Messages.messageUpvoteLeaderboard(channel)

                    event.hook.editOriginal("Indexed all channels. _($count messages)_")
                        .and(event.hook.editOriginalEmbeds(Embed {
                            title = "Most upvoted messages${if (channel != null) " in #${channel.name}" else ""}"
                            description = leaderboard.withIndex().joinToString("\n") { (i, spot) ->
                                val message = spot.first
                                val upvotes = spot.second

                                val jdaMessage = (event.guild?.getGuildChannelById(message.channel) as? MessageChannel)
                                    ?.retrieveMessageById(message.id)
                                    ?.complete()

                                var out = "**${i + 1}.** " +
                                        (if (jdaMessage != null) "[Link](${jdaMessage.jumpUrl}) - " else "") +
                                        "<t:${message.timestamp.toLocalDateTime().toEpochSecond(ZoneOffset.UTC)}:D>, " +
                                        "<@${message.author}>" +
                                        (if (channel == null) " in <#${message.channel}>" else "") +
                                        " with $upvotes upvotes\n"

                                message.contents?.let {
                                    out += "> " + it.lines().joinToString("\n> ") + "\n"
                                }

                                out
                            }
                        })).queue()
                }
            }
        }
    }

    Messages
}

fun Member.isAdmin() = id == "120593086844895234" || roles.any { it.name == "Staff" }

fun JDA.onCommandHandleErrors(name: String, consumer: suspend CoroutineEventListener.(SlashCommandEvent) -> Unit) {
    onCommand(name) {
        try {
            consumer(it)
        } catch (e: Throwable) {
            e.printStackTrace()
            it.hook.editOriginal("**ERROR!** Internal error: ${e.message}").queue()
        }
    }
}
