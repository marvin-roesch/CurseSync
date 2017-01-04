package de.mineformers.cursesync.sync.installer;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import de.mineformers.cursesync.sync.model.Mod;
import de.mineformers.cursesync.sync.model.PackManifest;

import java.io.File;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

import static de.mineformers.cursesync.sync.installer.InstallStep.Result.FAILURE;
import static de.mineformers.cursesync.sync.installer.InstallStep.Result.SUCCESS;

public class ClientInstaller extends Installer
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
        log.info("Automatic installation of Forge on the client is not supported yet, will download installer though...");

        log.info("Found primary mod loader '{}', installing...", loader.id);
        String version = Splitter.on("-").limit(2).splitToList(loader.id).get(1);
        String fullVersion = config.gameVersion + "-" + version;
        String fullName = "forge-" + fullVersion;
        log.info("Downloading Forge Client Installer v{}...", version);
        File installerFile = new File(config.tmpDirectory, "installers/forge-" + version + ".jar");
        try
        {
            if (!api.downloadFile(api.getURI("files.minecraftforge.net", "/maven/net/minecraftforge/forge/" + fullVersion + "/" + fullName + "-installer.jar", null), installerFile, 3))
            {
                log.error("Could not download required Forge installer, aborting!");
                return FAILURE;
            }
            log.info("Downloaded Forge Client Installer to '{}', please invoke it manually to install Forge!", installerFile.getAbsolutePath());
        }
        catch (URISyntaxException e)
        {
            log.error("Could not parse Forge server URL, aborting!", e);
            return FAILURE;
        }
        installation.forgeVersion = version;
        return SUCCESS;
    }

    @Override
    protected boolean acceptsMod(Mod mod)
    {
        return !mod.serverOnly;
    }
}
