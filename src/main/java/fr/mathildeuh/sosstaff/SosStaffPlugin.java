package fr.mathildeuh.sosstaff;

import com.zaxxer.hikari.HikariDataSource;
import fr.mathildeuh.sosstaff.command.PlayerCommands;
import fr.mathildeuh.sosstaff.config.ConfigManager;
import fr.mathildeuh.sosstaff.config.ConfigValidationException;
import fr.mathildeuh.sosstaff.config.StorageType;
import fr.mathildeuh.sosstaff.discord.ChannelOrchestrator;
import fr.mathildeuh.sosstaff.discord.DiscordGateway;
import fr.mathildeuh.sosstaff.lang.LangManager;
import fr.mathildeuh.sosstaff.storage.migration.MigrationRunner;
import fr.mathildeuh.sosstaff.storage.sqlite.SqliteDataSourceFactory;
import fr.mathildeuh.sosstaff.storage.sqlite.SqliteMigrations;
import fr.mathildeuh.sosstaff.storage.sqlite.SqliteTicketRepository;
import fr.mathildeuh.sosstaff.ticket.TicketRepository;
import fr.mathildeuh.sosstaff.ticket.TicketService;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SosStaffPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private LangManager langManager;
    private HikariDataSource dataSource;
    private ExecutorService storageExecutor;
    private TicketService ticketService;
    private DiscordGateway discordGateway;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        try {
            configManager.load();
        } catch (ConfigValidationException e) {
            getLogger().severe("Invalid config.yml, disabling: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        langManager = new LangManager(this, configManager.languageDefault(), configManager.languageShipped());
        langManager.load();

        if (configManager.storageType() != StorageType.SQLITE) {
            getLogger().severe("storage.type MYSQL is not implemented yet, disabling. Use SQLITE for now.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        dataSource = SqliteDataSourceFactory.create(getDataFolder().toPath().resolve("sosstaff.db"));
        try {
            new MigrationRunner(dataSource, SqliteMigrations.all()).run();
        } catch (SQLException e) {
            getLogger().severe("Failed to apply database migrations, disabling: " + e.getMessage());
            dataSource.close();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        storageExecutor = Executors.newFixedThreadPool(10, runnable -> {
            Thread thread = new Thread(runnable, "sosstaff-storage");
            thread.setDaemon(true);
            return thread;
        });
        TicketRepository ticketRepository = new SqliteTicketRepository(dataSource, storageExecutor);
        ticketService = new TicketService(ticketRepository, configManager);

        discordGateway = new DiscordGateway(getLogger());
        discordGateway.start(configManager.discord().token());
        ChannelOrchestrator channelOrchestrator = new ChannelOrchestrator(discordGateway, configManager, getLogger());

        new PlayerCommands(this, ticketService, configManager, langManager, channelOrchestrator).register();

        getLogger().info("SOS-Staff has been enabled.");
    }

    @Override
    public void onDisable() {
        if (discordGateway != null) {
            discordGateway.stop();
        }
        if (storageExecutor != null) {
            storageExecutor.shutdown();
        }
        if (dataSource != null) {
            dataSource.close();
        }
        getLogger().info("SOS-Staff has been disabled.");
    }

    public ConfigManager configManager() {
        return configManager;
    }

    public LangManager langManager() {
        return langManager;
    }

    public TicketService ticketService() {
        return ticketService;
    }
}
