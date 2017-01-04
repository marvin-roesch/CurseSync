package de.mineformers.cursesync.sync.installer;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import de.mineformers.cursesync.sync.model.Mod;
import de.mineformers.cursesync.sync.model.PackManifest;
import org.apache.commons.exec.*;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static de.mineformers.cursesync.sync.installer.InstallStep.Result.FAILURE;
import static de.mineformers.cursesync.sync.installer.InstallStep.Result.SUCCESS;

public class ServerInstaller extends Installer
{
    @Override
    protected List<InstallStep> constructSteps()
    {
        return ImmutableList.of(this::installModLoaders);
    }

    private InstallStep.Result installModLoaders()
    {
        log.info("Installing required mod loaders...");
        if (manifest.gameInfo.modLoaders.isEmpty())
        {
            log.info("No mod loaders required, continuing installation...");
            return SUCCESS;
        }
        Optional<PackManifest.ModLoader> primary = manifest.gameInfo.modLoaders.stream().filter(l -> l.primary).findFirst();
        if (!primary.isPresent())
        {
            log.error("No primary mod loader found, aborting!");
            return FAILURE;
        }
        PackManifest.ModLoader loader = primary.get();
        if (!loader.id.startsWith("forge"))
        {
            log.error("Unknown primary mod loader with id '{}', can't install. Note that CurseSync currently only supports Forge!", loader.id);
            return FAILURE;
        }
        log.info("Found primary mod loader '{}', installing...", loader.id);
        String version = Splitter.on("-").limit(2).splitToList(loader.id).get(1);
        if (installation.forgeVersion != null && !Objects.equals(installation.forgeVersion, version))
        {
            File oldUniversal = new File(config.output, "forge-" + installation.gameVersion + "-" + installation.forgeVersion + "-universal.jar");
            if (oldUniversal.exists())
            {
                log.info("Installed Forge Version {} is outdated, deleting old files...", installation.forgeVersion);
                if (!oldUniversal.delete())
                {
                    log.error("Failed to delete old Forge file '{}', aborting!", oldUniversal.getAbsolutePath());
                    return FAILURE;
                }
            }
        }
        if (!Objects.equals(installation.gameVersion, config.gameVersion))
        {
            File oldServer = new File(config.output, "minecraft_server." + installation.gameVersion + ".jar");
            if (oldServer.exists())
            {
                log.info("Installed MC Server Version {} is outdated, deleting old files...", installation.forgeVersion);
                if (!oldServer.delete())
                {
                    log.error("Failed to delete old server file '{}', aborting!", oldServer.getAbsolutePath());
                    return FAILURE;
                }
            }
        }
        String fullVersion = config.gameVersion + "-" + version;
        String fullName = "forge-" + fullVersion;
        File newUniversal = new File(config.output, fullName + "-universal.jar");
        if (newUniversal.exists())
        {
            log.info("Forge v{} already seems to be installed, skipping setup...", version);
            installation.forgeVersion = version;
            return SUCCESS;
        }
        log.info("Downloading Forge Server Installer v{}...", version);
        File installerFile = new File(config.tmpDirectory, "installers/forge-" + version + ".jar");
        try
        {
            if (!api.downloadFile(api.getURI("files.minecraftforge.net", "/maven/net/minecraftforge/forge/" + fullVersion + "/" + fullName + "-installer.jar", null), installerFile, 3))
            {
                log.error("Could not download required Forge installer, aborting!");
                return FAILURE;
            }
            log.info("Invoking Forge server installation...");
            String line = "java -jar " + installerFile.getAbsolutePath() + " --installServer";
            CommandLine cmdLine = CommandLine.parse(line);
            DefaultExecutor executor = new DefaultExecutor();
            executor.setWorkingDirectory(config.output);
            executor.setStreamHandler(new PumpStreamHandler(new LogOutputStream()
            {
                @Override
                protected void processLine(String line, int logLevel)
                {
                    log.info(line);
                }
            }, new LogOutputStream()
            {
                @Override
                protected void processLine(String line, int logLevel)
                {
                    log.error(line);
                }
            }));
            int exitValue = executor.execute(cmdLine);
            if (exitValue == 1)
            {
                return FAILURE;
            }
        }
        catch (URISyntaxException e)
        {
            log.error("Could not parse Forge server URL, aborting!", e);
            return FAILURE;
        }
        catch (ExecuteException e)
        {
            if (e.getExitValue() == 1)
            {
                log.error("The external installer could not finish successfully, most likely due to failing library downloads. Retry running CurseSync or install the server manually with {}!", installerFile.getAbsolutePath());
            }
            else
            {
                log.error("Could not successfully execute external installer process, aborting!", e);
            }
            return FAILURE;
        }
        catch (IOException e)
        {
            log.error("Could not successfully execute external installer process, aborting!", e);
            return FAILURE;
        }
        installation.forgeVersion = version;
        return SUCCESS;
    }

    @Override
    protected boolean acceptsMod(Mod mod)
    {
        return !mod.clientOnly;
    }
}
