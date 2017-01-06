package de.mineformers.cursesync;

import com.gluonhq.ignite.DIContext;
import com.gluonhq.ignite.guice.GuiceContext;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.stream.JsonWriter;
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import de.mineformers.cursesync.cli.CommandLineInterface;
import de.mineformers.cursesync.gui.GraphicalInterface;
import de.mineformers.cursesync.sync.CurseAPI;
import de.mineformers.cursesync.sync.installer.ClientInstaller;
import de.mineformers.cursesync.sync.installer.Installer;
import de.mineformers.cursesync.sync.installer.ServerInstaller;
import de.mineformers.cursesync.sync.mode.FileStrategy;
import de.mineformers.cursesync.sync.mode.Mode;
import de.mineformers.cursesync.sync.model.Installation;
import de.mineformers.cursesync.sync.model.ProjectVersion;
import de.mineformers.cursesync.util.DateTimeAdapter;
import de.mineformers.cursesync.util.FileAdapter;
import de.mineformers.cursesync.util.MultimapAdapter;
import de.mineformers.cursesync.util.SafeRedirectStrategy;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import joptsimple.ValueConverter;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.*;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class CurseSync
{
    public static String VERSION;

    public static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(FileAdapter.TYPE, new FileAdapter())
            .registerTypeAdapter(DateTimeAdapter.TYPE, new DateTimeAdapter())
            .registerTypeAdapter(new TypeToken<Multimap<String, ProjectVersion>>()
            {
            }.getType(), new MultimapAdapter())
            .create();

    public static void main(String[] args) throws IOException
    {
        CurseSync app = new CurseSync();
        app.run(args);
    }

    private Configuration config;
    private CurseSyncInterface client;
    private HttpClient http;
    private CurseAPI api;
    private ExecutorService executor;
    private GuiceContext context;
    private File configFile;

    private CurseSync()
    {
    }

    private void run(String[] args) throws IOException
    {
        Properties appProps = new Properties();
        try
        {
            appProps.load(CurseSync.class.getClassLoader().getResourceAsStream("app.properties"));
            VERSION = appProps.getProperty("version");
        }
        catch (IOException e)
        {
            System.err.println("Failed to load app.properties, aborting!");
            System.exit(1);
        }
        OptionSet options;
        try
        {
            options = Options.PARSER.parse(args);
        }
        catch (Exception exception)
        {
            System.err.println("Failed to parse options: " + exception.getMessage());
            String jarName = new java.io.File(CurseSync.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .getPath())
                    .getName();
            System.out.println("Usage: java -jar " + jarName + " <arguments>");
            Options.PARSER.printHelpOn(System.out);
            System.exit(1);
            return;
        }
        if (options.has(Options.HELP))
        {
            Options.PARSER.printHelpOn(System.out);
            return;
        }

        client = new CommandLineInterface();// options.has(Options.CLI) ? new CommandLineInterface() : new GraphicalInterface();
        config = loadConfig(client.log(), options);
        if (config == null)
        {
            System.exit(1);
        }
        saveConfig();
        executor = Executors.newFixedThreadPool(10);
        BasicCookieStore store = new BasicCookieStore();
        RequestConfig requestConfig = RequestConfig.custom()
                .setCircularRedirectsAllowed(true)
                .setConnectionRequestTimeout(30000)
                .setConnectTimeout(30000)
                .setSocketTimeout(30000)
                .build();
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(20);
        http = HttpClientBuilder.create()
                .setUserAgent("CurseSync")
                .setDefaultCookieStore(store)
                .setDefaultRequestConfig(requestConfig)
                .setRedirectStrategy(SafeRedirectStrategy.INSTANCE)
                .setConnectionManager(cm)
                .build();
        context = new GuiceContext(this, () -> ImmutableList.of(new GuiceModule()));
        context.init();
        api = new CurseAPI();
        context.injectMembers(api);
        context.injectMembers(client);
        client.run();
    }

    @Nullable
    private Configuration loadConfig(Logger log, OptionSet options)
    {
        configFile = getArgument(options, Options.CONFIG_FILE, new File("./cursesync-config.json"));
        String projectSlug = getArgument(options, Options.PROJECT_SLUG, null);
        String gameVersion = getArgument(options, Options.GAME_VERSION, null);
        String projectVersion = getArgument(options, Options.PROJECT_VERSION, null);
        File output = getArgument(options, Options.OUTPUT_DIR, null);
        Mode mode = getArgument(options, Options.MODE, Mode.UPDATE);
        boolean server = options.has(Options.SERVER);
        File tmpDirectory = getArgument(options, Options.TMP_DIR, null);
        boolean failDiscrepancies = options.has(Options.FAIL_DISCREPANCIES);
        Configuration config = new Configuration(projectSlug, gameVersion, projectVersion, output, mode, server, tmpDirectory, failDiscrepancies);
        if (configFile.exists())
        {
            try
            {
                config = GSON.fromJson(new FileReader(configFile), Configuration.class);
                // Command line options take precedence over config file
                if (projectSlug != null)
                    config.projectSlug = projectSlug;
                if (gameVersion != null)
                    config.gameVersion = gameVersion;
                if (projectVersion != null)
                    config.projectVersion = projectVersion;
                if (output != null)
                    config.output = output;
                if (mode != null && options.has(Options.MODE))
                    config.mode = mode;
                if (server && !config.server)
                    config.server = true;
                if (tmpDirectory != null)
                    config.tmpDirectory = tmpDirectory;
                if (failDiscrepancies && !config.failDiscrepancies)
                    config.failDiscrepancies = true;
            }
            catch (JsonParseException exception)
            {
                log.error("Failed to parse configuration file.", exception);
                return null;
            }
            catch (FileNotFoundException exception)
            {
                // Can't happen, we check its existence.
            }
        }
        else if (options.has(Options.CONFIG_FILE))
        {
            log.error("Specified configuration file does not exist: {}", configFile.getAbsolutePath());
            return null;
        }
        config.output = config.output == null ? null : config.output.toPath().toAbsolutePath().normalize().toFile();
        config.tmpDirectory = config.tmpDirectory == null ? null : config.tmpDirectory.toPath().toAbsolutePath().normalize().toFile();
        return config;
    }

    private void saveConfig()
    {
        try
        {
            BufferedWriter writer = new BufferedWriter(new FileWriter(configFile));
            JsonWriter jsonWriter = new JsonWriter(writer);
            jsonWriter.setIndent("  ");
            CurseSync.GSON.toJson(config, Configuration.class, jsonWriter);
            writer.close();
        }
        catch (IOException e)
        {
            System.err.println("Failed to write configuration back to disk, aborting!");
        }
    }

    private <T> T getArgument(OptionSet options, OptionSpec<T> option, T defaultValue)
    {
        if (options.has(option))
        {
            return options.valueOf(option);
        }
        else
        {
            return defaultValue;
        }
    }

    public void shutdownExecutor()
    {
        executor.shutdown();
        try
        {
            executor.awaitTermination(1, TimeUnit.SECONDS);
        }
        catch (InterruptedException e)
        {
            client.log().error("Could not terminate all running asynchronous operations within 1s, forcing a shutdown...", e);
        }
        executor.shutdownNow();
    }

    public void shutdown(int code)
    {
        shutdownExecutor();
        System.exit(1);
    }

    public static class Options
    {
        public static final OptionParser PARSER = new OptionParser();
        public static final OptionSpec HELP =
                PARSER.acceptsAll(ImmutableList.of("h", "help", "?"), "Displays this help page.");
        public static final OptionSpec<File> CONFIG_FILE =
                PARSER.accepts("config",
                        "The configuration file to use for this launch of the application.")
                        .withRequiredArg()
                        .describedAs("config file")
                        .ofType(File.class);
        public static final OptionSpec CLI =
                PARSER.acceptsAll(ImmutableList.of("c", "cli"),
                        "Makes the utility only run on the command client without GUI.");
        public static final OptionSpec SERVER =
                PARSER.acceptsAll(ImmutableList.of("s", "server"),
                        "Specifies that the modpack is to be installed as a server.");
        public static final OptionSpec<String> PROJECT_SLUG =
                PARSER.acceptsAll(ImmutableList.of("p", "project"),
                        "The slug of the CurseForge projectSlug (i.e. modpack) to download.")
                        .withRequiredArg()
                        .describedAs("project-slug")
                        .ofType(String.class);
        public static final OptionSpec<String> GAME_VERSION =
                PARSER.acceptsAll(ImmutableList.of("g", "gv", "game", "game-version"),
                        "The version of Minecraft the modpack is for.")
                        .withRequiredArg()
                        .describedAs("MC version")
                        .ofType(String.class);
        public static final OptionSpec<String> PROJECT_VERSION =
                PARSER.acceptsAll(ImmutableList.of("v", "pv", "version", "project-version"),
                        "The version of the modpack to download.")
                        .withRequiredArg()
                        .describedAs("version")
                        .ofType(String.class);
        public static final OptionSpec<File> OUTPUT_DIR =
                PARSER.acceptsAll(ImmutableList.of("o", "output", "dest", "destination"),
                        "The folder to install/update the modpack into. Will use the current directory if not specified.")
                        .withRequiredArg()
                        .describedAs("directory")
                        .ofType(File.class);
        public static final OptionSpec<File> TMP_DIR =
                PARSER.acceptsAll(ImmutableList.of("t", "tmp", "temp", "temporary"),
                        "The folder to store temporary files etc. into. Also the default location for the mod repository.")
                        .withRequiredArg()
                        .describedAs("directory")
                        .ofType(File.class);
        public static final OptionSpec<Mode> MODE =
                PARSER.acceptsAll(ImmutableList.of("m", "mode"),
                        "Determines how to install the modpack. Note that 'install' will do nothing if the output directory " +
                                "already contains an installation and that 'overwrite' will completely wipe the output directory! " +
                                "'update' (the default mode) will install the pack if there is no installation yet and will apply all " +
                                "changes from the selected version.")
                        .withRequiredArg()
                        .describedAs("install | overwrite | update")
                        .ofType(Mode.class)
                        .withValuesConvertedBy(new ValueConverter<Mode>()
                        {
                            @Override
                            public Mode convert(String value)
                            {
                                return Mode.valueOf(value.toUpperCase());
                            }

                            @Override
                            public Class<? extends Mode> valueType()
                            {
                                return Mode.class;
                            }

                            @Override
                            public String valuePattern()
                            {
                                return "install|update|overwrite";
                            }
                        });
        public static final OptionSpec FAIL_DISCREPANCIES =
                PARSER.accepts("fail-discrepancies",
                        "Determines whether the installation should fail if there are any checksum discrepancies for overrides.");
    }

    public static class Configuration
    {
        @Nullable
        public String projectSlug;
        @Nullable
        public String gameVersion;
        @Nullable
        public String projectVersion;
        @Nullable
        public File output;
        @Nullable
        public Mode mode;
        public boolean server;
        @Nullable
        public File tmpDirectory;
        public boolean failDiscrepancies;

        public Configuration(@Nullable String projectSlug, @Nullable String gameVersion, @Nullable String projectVersion, @Nullable File output, @Nullable Mode mode, boolean server, @Nullable File tmpDirectory, boolean failDiscrepancies)
        {
            this.projectSlug = projectSlug;
            this.gameVersion = gameVersion;
            this.projectVersion = projectVersion;
            this.output = output != null ? output.toPath().toAbsolutePath().normalize().toFile() : null;
            this.mode = mode;
            this.server = server;
            this.tmpDirectory = tmpDirectory == null ? new File("./tmp").toPath().toAbsolutePath().normalize().toFile() : tmpDirectory;
            this.failDiscrepancies = failDiscrepancies;
        }

        public File installationFile()
        {
            return new File(output, "cursesync-installation.json");
        }

        public int projectId()
        {
            if (projectSlug == null)
                return 0;
            List<String> slugParts = Splitter.on('-').limit(2).splitToList(projectSlug);
            return Integer.parseInt(slugParts.get(0));
        }

        @Nonnull
        public String projectNameSlug()
        {
            if (projectSlug == null)
                return "unknown";
            List<String> slugParts = Splitter.on('-').limit(2).splitToList(projectSlug);
            return slugParts.get(1);
        }

        public boolean valid()
        {
            if (output != null && installationFile().exists())
            {
                return true;
            }
            return projectSlug != null && gameVersion != null && projectVersion != null && output != null && mode != null;
        }

        public void dump(Logger log, Level level)
        {
            log.log(level, "Project Slug: {}", projectSlug == null ? "n/a" : projectSlug);
            log.log(level, "Game Version: {}", gameVersion == null ? "n/a" : gameVersion);
            log.log(level, "Project Version: {}", projectVersion == null ? "n/a" : projectVersion);
            log.log(level, "Output Directory: {}", output == null ? "n/a" : output.getAbsolutePath());
            log.log(level, "Temporary Files Directory: {}", tmpDirectory == null ? "n/a" : tmpDirectory.getAbsolutePath());
            log.log(level, "Installation mode: {}", mode == null ? "n/a" : mode.name().toLowerCase());
            log.log(level, "Server Mode: {}", server);
        }
    }

    private class GuiceModule extends AbstractModule
    {
        private Installer installer;
        private Installation installation;
        private boolean injectedStrategy;

        @Override
        protected void configure()
        {
            bind(DIContext.class).toInstance(context);
            bind(CurseSyncInterface.class).toProvider(() -> client);
            bind(Configuration.class).toInstance(config);
            bind(ExecutorService.class).toInstance(executor);
            bind(CurseSync.class).toInstance(CurseSync.this);
            bind(HttpClient.class).toInstance(http);
            bind(CurseAPI.class).toProvider(() -> api);
            bind(Logger.class).toProvider(client::log);
            bind(Executor.class).annotatedWith(Names.named("UI")).toProvider(client::uiExecutor);
            bind(Installer.class).toProvider(() ->
            {
                if (installer == null)
                    installer = config.server ? new ServerInstaller() : new ClientInstaller();
                return installer;
            });
            bind(FileStrategy.class).toProvider(() ->
            {
                if (config.mode == null)
                    return null;
                if (!injectedStrategy)
                {
                    context.injectMembers(config.mode.strategy);
                    injectedStrategy = true;
                }
                return config.mode.strategy;
            });
            bind(Installation.class).toProvider(() ->
            {
                if (installation == null)
                {
                    File installationFile = config.installationFile();
                    if (config.output == null || !installationFile.exists())
                    {
                        if (config.projectSlug == null || config.tmpDirectory == null || config.gameVersion == null)
                            return null;
                        return (installation =
                                new Installation(config.projectId(), config.projectNameSlug(), config.gameVersion, config.server,
                                        new File(config.tmpDirectory.getAbsoluteFile(), "mods/"), null, null, null, null));
                    }
                    try
                    {
                        return GSON.fromJson(new FileReader(installationFile), Installation.class);
                    }
                    catch (JsonParseException exception)
                    {
                        client.log().error("Failed to parse installation configuration file.", exception);
                        return null;
                    }
                    catch (FileNotFoundException e)
                    {
                        // Shouldn't happen...
                        return null;
                    }
                }
                return installation;
            });
            requestStaticInjection(GraphicalInterface.class);
        }
    }
}
