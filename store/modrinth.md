# SOS-Staff

**Free, open-source support-ticket management for Paper, with a full bidirectional Discord bridge.**

Players open tickets in-game. Staff manage them from Discord. Live chat relays both ways for as
long as the ticket stays open - no server-hopping between Minecraft and Discord to get help.

## Features

- **Two creation flows** - a preset list of common issues, free-form text (anvil GUI or chat), or
  both, configurable globally and per category.
- **Anti-spam** - a configurable cap on open tickets per player and a cooldown after closing one.
- **Bidirectional live chat** - a player's in-game messages relay instantly to their ticket's
  Discord channel via webhook (their skin, their name), and staff replies relay back in-game.
- **One Discord channel per ticket**, in a dedicated category, with claim/close/reopen/priority/
  transcript/ping buttons and fully configurable, permission-gated action buttons (heal, feed,
  force-reconnect, teleport, freeze, or any raw command).
- **In-game admin panel** (`/sostaff panel`) - a paginated, filterable ticket list with player
  heads, status-colored wool, and click-to-teleport-and-attach-chat.
- **Five shipped languages** at parity: English, French, Spanish, Russian, German - falling back
  to English for any missing key.
- **Hot reload** (`/sostaff reload`) - config, language files, and the Discord connection,
  without ever disrupting a ticket already open.
- **GDPR tooling** - `/sostaff gdpr erase <player>` and configurable transcript retention.
- **A public API and Bukkit events** for other plugins to read, create, and react to tickets.
- **Soft-depend integrations** - PlaceholderAPI, LuckPerms, Vault.
- **Folia-safe** throughout: no blocking calls on the main or a region thread, and no direct
  Bukkit access from a Discord callback thread.

## Requirements

Paper (or a fork such as Purpur/DivineMC) **26.2 or newer** - which itself requires **Java 25** to
run. A Discord bot application of your own (free, five minutes to set up - see the wiki).

## Links

- [Source code](https://github.com/Mathildeuh/SOS-Staff)
- [Wiki & setup guide](https://github.com/Mathildeuh/SOS-Staff/wiki)
- [Report an issue](https://github.com/Mathildeuh/SOS-Staff/issues)

## License

GPL-3.0 - 100% free and open source. No license keys, activation, or premium tiers exist or are
planned.
