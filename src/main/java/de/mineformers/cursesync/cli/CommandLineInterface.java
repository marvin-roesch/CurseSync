package de.mineformers.cursesync.cli;

import com.gluonhq.ignite.DIContext;
import com.google.inject.Inject;
import de.mineformers.cursesync.CurseSync;
import de.mineformers.cursesync.CurseSyncInterface;
import de.mineformers.cursesync.sync.CurseAPI;
import de.mineformers.cursesync.sync.installer.Installer;
import de.mineformers.cursesync.sync.model.CurseProject;
import de.mineformers.cursesync.sync.model.Installation;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

public class CommandLineInterface implements CurseSyncInterface
{
    @Nonnull
    private final Logger log = LogManager.getLogger(CommandLineInterface.class);
    private final Executor uiExecutor = Runnable::run;
    @Inject
    private CurseSync app;
    @Inject
    private DIContext context;
    @Inject
    private ExecutorService executor;
    @Inject
    private CurseAPI api;
    @Inject
    private CurseSync.Configuration config;

    @Nonnull
    @Override
    public Logger log()
    {
        return log;
    }

    @Nonnull
    @Override
    public Executor uiExecutor()
    {
        return uiExecutor;
    }

    @Override
    public void run()
    {
        if (!config.valid())
        {
            log.error("==================================================================================");
            log.error("Could not start CurseSync Command Line Client due to missing configuration values.");
            log.error("The loaded configuration looks as follows:");
            config.dump(log, Level.ERROR);
            log.error("Add the missing values to your configuration file or specify them via");
            log.error("command line arguments.");
            log.error("==================================================================================");
            app.shutdown(1);
        }
        log.info("============================================================================");
        log.info("Starting CurseSync Command Line Client v{} with the following configuration:", CurseSync.VERSION);
        if (config.installationFile().exists())
        {
            log.info("Existing installation in output directory found, added missing information...");
            Installation installation = context.getInstance(Installation.class);
            if (installation != null)
            {
                if (config.projectSlug == null)
                    config.projectSlug = installation.projectSlug;
                if (config.projectVersion == null)
                    config.projectVersion = installation.lastFile;
                if (config.gameVersion == null)
                    config.gameVersion = installation.gameVersion;
            }
        }
        config.dump(log, Level.INFO);
        log.info("============================================================================");
        log.info("Validating configuration...");
        CurseProject project;
        try
        {
            project = validateConfig().get();
            if (project == null)
            {
                app.shutdown(1);
                return;
            }
        }
        catch (InterruptedException | ExecutionException e)
        {
            log.error("Exception while trying to validate configuration, shutting down!", e);
            app.shutdown(1);
            return;
        }
        log.info("Configuration appears to be valid, beginning execution...");
        log.info("Starting {} installation in mode '{}'...", config.server ? "server" : "client", config.mode);
        Installer installer = context.getInstance(Installer.class);
        context.injectMembers(installer);
        installer.init(project);
        if (!installer.execute())
        {
            app.shutdown(1);
            return;
        }
        app.shutdown(0);
    }

    private CompletableFuture<CurseProject> validateConfig()
    {
        // Should never happen, makes IDEs shut up, though
        if (config.projectSlug == null || config.projectVersion == null)
        {
            log.error("Project slug or version unexpectedly were null, aborting!");
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture
                .supplyAsync(() -> api.getModpack(config.projectSlug), executor)
                .thenApplyAsync(result ->
                {
                    if (result.title == null)
                    {
                        log.error("The specified project does not exist, aborting!");
                        return null;
                    }
                    if (!result.versions.containsKey(config.gameVersion))
                    {
                        log.error("The pack does not exist for game version '{}', aborting!", config.gameVersion);
                        return null;
                    }
                    if (result.versions.get(config.gameVersion).stream().noneMatch(v -> v.name.contains(config.projectVersion)))
                    {
                        log.error("The pack has no version matching '{}', aborting!", config.projectVersion);
                        return null;
                    }
                    return result;
                }, uiExecutor);
    }
}
