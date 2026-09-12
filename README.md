# SOS-Staff

A free, open-source Minecraft (Paper) support-ticket plugin with a full bidirectional Discord
bridge: players open tickets in-game, staff manage them from Discord, and live chat is relayed
both ways for as long as the ticket stays open.

## Features

- **Two creation flows** - a preset list of common issues, free-form text (anvil GUI or chat
  prompt), or both, configurable globally and per category.
- **Anti-spam** - a configurable cap on open tickets per player and a cooldown after closing one.
- **Bidirectional live chat** - a player's in-game messages relay instantly to their ticket's
  Discord channel via webhook (with their skin and name), and staff replies relay back in-game.
- **One Discord channel per ticket**, in a dedicated category, with claim/close/reopen/priority/
  transcript/ping buttons and fully configurable, permission-gated action buttons (heal, feed,
  force-reconnect, teleport, freeze, or any raw command).
- **In-game admin panel** (`/sostaff panel`) - a paginated, filterable ticket list with player
  heads, status-colored wool, and click-to-teleport-and-attach-chat.
- **Five shipped languages** at parity (`en_US`, `fr_FR`, `es_ES`, `ru_RU`, `de_DE`), falling
  back to `en_US` for any missing key.
- **Hot reload** (`/sostaff reload`) - config, language files, and the Discord connection, without
  ever disrupting a ticket already open.
- **GDPR tooling** - `/sostaff gdpr erase <player>` and configurable transcript retention.
- **A public API and Bukkit events** for other plugins to read, create, and react to tickets.
- **Folia-safe** throughout: no blocking calls on the main or a region thread, and no direct
  Bukkit access from a Discord callback thread.

## Requirements

- Paper (or a fork such as Purpur/DivineMC) **26.2 or newer**. Paper 26.2 itself requires Java 25
  to run, so that is this plugin's floor too - there is no build that runs on an older JDK.
- A Discord application/bot with the **Server Members** and **Message Content** privileged
  intents enabled, invited to your server with permission to manage channels, roles, and webhooks.

## Installation

1. Drop the built jar (`SOS-Staff-<version>-all.jar` from `build/libs/`, or a release download)
   into your server's `plugins/` folder and start the server once to generate `config.yml`.
2. Set your bot token as an environment variable rather than writing it into `config.yml`:
   ```
   SOSSTAFF_DISCORD_TOKEN=your-bot-token
   ```
   (If `config.yml`'s `discord.token` is non-blank, it is used as a fallback, but the environment
   variable always wins - keep the token out of the file you might otherwise commit or share.)
3. Edit `config.yml` - at minimum `discord.category`, `discord.permissions.staff-roles`, and your
   ticket `categories` - then `/sostaff reload`.

## Commands

| Command | Description | Permission |
|---|---|---|
| `/ticket` (aliases: `/support`, `/sos`) | Show your active ticket's status | - |
| `/ticket new` | Open the ticket-creation menu | - |
| `/ticket new <category>` | Create a ticket directly, no opening message | - |
| `/ticket status` | Same as `/ticket` | - |
| `/ticket list` | Show your ticket history | - |
| `/ticket close [reason]` | Close your active ticket | - |
| `/ticket cancel` | Close your active ticket with no reason recorded | - |
| `/sostaff panel` | Open the admin ticket panel | `sosstaff.admin.panel` |
| `/sostaff reload` | Reload config, language files, and the Discord connection | `sosstaff.admin.reload` |
| `/sostaff gdpr erase <player>` | Erase all of a player's tickets and messages | `sosstaff.admin.gdpr` |

The command's main label and aliases are themselves configurable (`commands.main`/`commands.aliases`
in `config.yml`). Every configured action button also gets its own permission node
(`action-buttons.<id>.permission` in `config.yml`), and `sosstaff.bypass.antispam` skips the
open-ticket cap and cooldown.

## Developer API

Other plugins can read and create tickets through `SosStaffAPI`, obtained via Bukkit's
`ServicesManager` once SOS-Staff has enabled:

```java
SosStaffAPI api = Bukkit.getServicesManager().getRegistration(SosStaffAPI.class).getProvider();
```

`getActiveTicket`/`getTicketHistory` answer synchronously from an in-memory cache (never blocking
on the database); `createTicket` returns a `CompletableFuture`. Four Bukkit events -
`TicketCreateEvent`, `TicketClaimEvent`, `TicketCloseEvent`, `TicketMessageEvent` - are fired
around ticket activity and are `Cancellable`. See the Javadoc on the `fr.mathildeuh.sosstaff.api`
package for the full contract.

## Soft-depend integrations

None of these are required; each is only activated if the corresponding plugin is installed.

- **PlaceholderAPI** - `%sosstaff_active_id%`, `%sosstaff_active_status%`,
  `%sosstaff_active_priority%`, `%sosstaff_ticket_count%`.
- **LuckPerms** and **Vault** - detected and logged as a seam for future features; nothing in
  SOS-Staff calls into either yet.

## Building from source

```
./gradlew build
```

produces `build/libs/SOS-Staff-<version>-all.jar` (the shaded jar to actually deploy) after
running the full JUnit/MockBukkit test suite, including a check that all five `lang/*.yml` files
define exactly the same keys as the `en_US` pivot.

## CI/CD

Three GitHub Actions workflows live under `.github/workflows/`:

- **`ci.yml`** - build, test, and commit-message lint on every push and pull request.
- **`dev-build.yml`** - uploads a numbered build artifact on every push to `develop`.
- **`release.yml`** - manual-only for now (run it from the Actions tab). It classifies the
  commits since the last tag (`feat` -> minor, a breaking change -> major, anything else ->
  patch), bumps and tags the version with Axion Release, generates the changelog with git-cliff,
  publishes a GitHub Release with the jar and its SHA-256 checksum, publishes to Modrinth and
  Hangar, and posts a Discord notification. SpigotMC has no publishing API, so that listing
  still needs a manual update - the workflow leaves a reminder in its own job summary. Switch its
  trigger back to `push: branches: [main]` once the table below is filled in and a fully
  automatic release pipeline is actually wanted.

`release.yml` needs the following repository secrets and variables configured before it can
actually publish anything (it will simply fail those specific steps until they are set):

| Name | Kind | Purpose |
|---|---|---|
| `MODRINTH_TOKEN` | secret | Modrinth publish |
| `MODRINTH_PROJECT_ID` | variable | Modrinth publish |
| `HANGAR_API_TOKEN` | secret | Hangar publish |
| `HANGAR_PROJECT_SLUG` | variable | Hangar publish |
| `DISCORD_RELEASE_WEBHOOK` | secret | Discord release notification |

`GITHUB_TOKEN` (tagging, pushing, and creating the GitHub Release) is provided automatically by
GitHub Actions and needs no setup. The bStats service id in `SosStaffPlugin` is already
registered.

## License

GPL-3.0 - see [LICENSE](LICENSE). No license keys, activation, or premium tiers exist or are
planned; this project is 100% free and open source.
