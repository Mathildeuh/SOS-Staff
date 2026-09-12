package fr.mathildeuh.sosstaff;

import com.zaxxer.hikari.HikariDataSource;
import fr.mathildeuh.sosstaff.api.SosStaffAPI;
import fr.mathildeuh.sosstaff.api.SosStaffAPIImpl;
import fr.mathildeuh.sosstaff.command.AdminCommands;
import fr.mathildeuh.sosstaff.command.PlayerCommands;
import fr.mathildeuh.sosstaff.config.ConfigManager;
import fr.mathildeuh.sosstaff.config.ConfigValidationException;
import fr.mathildeuh.sosstaff.config.ReloadService;
import fr.mathildeuh.sosstaff.config.StorageType;
import fr.mathildeuh.sosstaff.discord.ActionButtonHandler;
import fr.mathildeuh.sosstaff.discord.ButtonHandler;
import fr.mathildeuh.sosstaff.discord.ChannelOrchestrator;
import fr.mathildeuh.sosstaff.discord.DiscordGateway;
import fr.mathildeuh.sosstaff.discord.DiscordInteractionListener;
import fr.mathildeuh.sosstaff.discord.DiscordMessageListener;
import fr.mathildeuh.sosstaff.discord.EscalationScheduler;
import fr.mathildeuh.sosstaff.discord.FreezeListener;
import fr.mathildeuh.sosstaff.discord.FrozenPlayers;
import fr.mathildeuh.sosstaff.discord.InternalActionRegistry;
import fr.mathildeuh.sosstaff.discord.WebhookRelay;
import fr.mathildeuh.sosstaff.gui.AdminPanel;
import fr.mathildeuh.sosstaff.gui.AnvilInputGui;
import fr.mathildeuh.sosstaff.gui.CreationMenu;
import fr.mathildeuh.sosstaff.gui.GuiClickListener;
import fr.mathildeuh.sosstaff.gui.PendingChatPrompts;
import fr.mathildeuh.sosstaff.integration.LuckPermsHook;
import fr.mathildeuh.sosstaff.integration.PlaceholderApiHook;
import fr.mathildeuh.sosstaff.integration.VaultHook;
import fr.mathildeuh.sosstaff.lang.LangManager;
import fr.mathildeuh.sosstaff.session.LiveChatListener;
import fr.mathildeuh.sosstaff.session.LiveChatSessionManager;
import fr.mathildeuh.sosstaff.storage.migration.MigrationRunner;
import fr.mathildeuh.sosstaff.storage.sqlite.SqliteDataSourceFactory;
import fr.mathildeuh.sosstaff.storage.sqlite.SqliteMigrations;
import fr.mathildeuh.sosstaff.storage.sqlite.SqliteTicketMessageRepository;
import fr.mathildeuh.sosstaff.storage.sqlite.SqliteTicketRepository;
import fr.mathildeuh.sosstaff.ticket.GdprService;
import fr.mathildeuh.sosstaff.ticket.RetentionScheduler;
import fr.mathildeuh.sosstaff.ticket.TicketCreationCoordinator;
import fr.mathildeuh.sosstaff.ticket.TicketMessageRepository;
import fr.mathildeuh.sosstaff.ticket.TicketRepository;
import fr.mathildeuh.sosstaff.ticket.TicketService;
import fr.mathildeuh.sosstaff.util.MetricsCharts;
import fr.mathildeuh.sosstaff.util.UpdateChecker;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PaperSimpleSenderMapper;
import org.incendo.cloud.paper.util.sender.Source;

import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SosStaffPlugin extends JavaPlugin {

    private static final int BSTATS_SERVICE_ID = 33997;

    private ConfigManager configManager;
    private LangManager langManager;
    private HikariDataSource dataSource;
    private ExecutorService storageExecutor;
    private TicketService ticketService;
    private DiscordGateway discordGateway;
    private SosStaffAPI sosStaffAPI;

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
        TicketMessageRepository ticketMessageRepository = new SqliteTicketMessageRepository(dataSource, storageExecutor);

        LiveChatSessionManager sessionManager = new LiveChatSessionManager();
        PendingChatPrompts pendingChatPrompts = new PendingChatPrompts();

        FrozenPlayers frozenPlayers = new FrozenPlayers();
        InternalActionRegistry internalActionRegistry = new InternalActionRegistry(frozenPlayers);

        discordGateway = new DiscordGateway(getLogger());
        EscalationScheduler escalationScheduler = new EscalationScheduler(this, ticketService, configManager, discordGateway, getLogger());
        ButtonHandler buttonHandler = new ButtonHandler(
                this, ticketService, configManager, ticketMessageRepository, sessionManager, escalationScheduler);
        ActionButtonHandler actionButtonHandler = new ActionButtonHandler(this, configManager, ticketService, internalActionRegistry);

        DiscordMessageListener discordMessageListener =
                new DiscordMessageListener(this, sessionManager, ticketMessageRepository, ticketService, langManager);
        DiscordInteractionListener discordInteractionListener = new DiscordInteractionListener(buttonHandler, actionButtonHandler);
        discordGateway.start(configManager.discord().token(), discordMessageListener, discordInteractionListener);
        escalationScheduler.start();

        GdprService gdprService = new GdprService(ticketRepository, ticketMessageRepository, configManager);
        new RetentionScheduler(this, gdprService, getLogger()).start();

        ReloadService reloadService = new ReloadService(configManager, langManager, newToken -> {
            discordGateway.stop();
            discordGateway.start(newToken, discordMessageListener, discordInteractionListener);
        });

        ChannelOrchestrator channelOrchestrator = new ChannelOrchestrator(discordGateway, configManager, getLogger());
        WebhookRelay webhookRelay = new WebhookRelay(discordGateway, getLogger());
        TicketCreationCoordinator creationCoordinator =
                new TicketCreationCoordinator(ticketService, channelOrchestrator, sessionManager, ticketMessageRepository, webhookRelay);

        sosStaffAPI = new SosStaffAPIImpl(ticketService, creationCoordinator);
        getServer().getServicesManager().register(SosStaffAPI.class, sosStaffAPI, this, ServicePriority.Normal);

        LiveChatListener liveChatListener = new LiveChatListener(
                this, sessionManager, webhookRelay, ticketMessageRepository, ticketService, pendingChatPrompts, getLogger());
        getServer().getPluginManager().registerEvents(liveChatListener, this);
        getServer().getPluginManager().registerEvents(new FreezeListener(frozenPlayers), this);

        AnvilInputGui anvilInputGui = new AnvilInputGui();
        getServer().getPluginManager().registerEvents(anvilInputGui, this);
        CreationMenu creationMenu = new CreationMenu(this, configManager, langManager, creationCoordinator, anvilInputGui, pendingChatPrompts);
        AdminPanel adminPanel = new AdminPanel(this, ticketService, configManager, langManager, sessionManager);
        getServer().getPluginManager().registerEvents(new GuiClickListener(creationMenu, adminPanel), this);

        PaperCommandManager<Source> commandManager = PaperCommandManager
                .builder(PaperSimpleSenderMapper.simpleSenderMapper())
                .executionCoordinator(ExecutionCoordinator.simpleCoordinator())
                .buildOnEnable(this);
        new PlayerCommands(this, ticketService, configManager, langManager, sessionManager, creationCoordinator, creationMenu)
                .register(commandManager);
        new AdminCommands(this, adminPanel, reloadService, gdprService, langManager).register(commandManager);

        getServer().getOnlinePlayers().forEach(liveChatListener::reopenSessionIfNeeded);

        registerSoftDependIntegrations();
        MetricsCharts.register(new Metrics(this, BSTATS_SERVICE_ID), configManager, discordGateway);
        if (configManager.updateCheckerEnabled()) {
            new UpdateChecker(getLogger()).checkAsync();
        }

        getLogger().info("SOS-Staff has been enabled.");
    }

    private void registerSoftDependIntegrations() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlaceholderApiHook(this, ticketService).register();
        }
        if (getServer().getPluginManager().getPlugin("LuckPerms") != null) {
            new LuckPermsHook(getLogger());
        }
        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            new VaultHook(getServer().getServicesManager(), getLogger());
        }
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
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

    public SosStaffAPI sosStaffAPI() {
        return sosStaffAPI;
    }
}
