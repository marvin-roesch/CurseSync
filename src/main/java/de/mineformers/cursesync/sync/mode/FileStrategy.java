package de.mineformers.cursesync.sync.mode;

import com.google.inject.Inject;
import de.mineformers.cursesync.CurseSync;
import de.mineformers.cursesync.sync.model.FileOverride;
import de.mineformers.cursesync.sync.model.ForgeModList;
import de.mineformers.cursesync.sync.model.Installation;
import de.mineformers.cursesync.sync.model.Mod;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public abstract class FileStrategy
{
    @Inject
    protected Logger log;
    @Inject
    protected CurseSync.Configuration config;
    @Inject
    protected Installation installation;

    public boolean canInstall()
    {
        if (config.server != installation.server)
        {
            log.error("The existing installation in the output directory is for a different side than the current configuration, aborting!");
            return false;
        }
        return true;
    }

    public boolean prepareDirectory()
    {
        if (config.output == null)
        {
            log.error("Output directory unexpectedly was null!");
            return false;
        }
        if (!config.output.exists() && !config.output.mkdirs())
        {
            log.error("Failed to create output directory!");
            return false;
        }
        return true;
    }

    public ForgeModList mergeModLists(ForgeModList old, List<Mod> newMods, Predicate<Mod> acceptor)
    {
        log.info("Mod List Merge Strategy: Overwrite old mods");
        List<String> modRefs = newMods.stream()
                .filter(acceptor)
                .map(Mod::dependencyString)
                .collect(Collectors.toList());
        old.modRef.clear();
        old.modRef.addAll(modRefs);
        return old;
    }

    public boolean validateOldChecksums(File directory)
    {
        return true;
    }

    public boolean deleteOldOverrides(File directory, List<FileOverride> overrides)
    {
        boolean success = true;
        if (installation.overrides != null)
        {
            for (FileOverride override : installation.overrides)
            {
                File overrideFile = new File(directory, override.path);
                if (!overrideFile.exists())
                {
                    log.info("Override file '{}' doesn't exist anymore, ignoring it.", override.path);
                    continue;
                }
                try
                {
                    InputStream digestStream = new FileInputStream(overrideFile);
                    String checksum = DigestUtils.md5Hex(digestStream);
                    digestStream.close();
                    if (overrides.stream().anyMatch(o -> Objects.equals(o.path, override.path) && Objects.equals(checksum, override.checksum)))
                    {
                        log.info("Override file '{}' exists and matches the new checksum, keeping it.", override.path);
                        continue;
                    }
                    try
                    {
                        FileUtils.forceDelete(overrideFile);
                    }
                    catch (IOException e)
                    {
                        log.error(log.getMessageFactory().newMessage("Could not delete file '{}'!", overrideFile.getAbsolutePath()), e);
                        success = false;
                    }
                }
                catch (IOException e)
                {
                    log.error(log.getMessageFactory().newMessage("Could not calculate checksum for file '{}'!", overrideFile.getAbsolutePath()), e);
                    success = false;
                }
            }
        }
        return success;
    }
}
