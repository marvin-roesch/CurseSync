package de.mineformers.cursesync.sync.mode;

import de.mineformers.cursesync.sync.model.FileOverride;
import de.mineformers.cursesync.sync.model.ForgeModList;
import de.mineformers.cursesync.sync.model.Mod;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.message.FormattedMessageFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class UpdateStrategy extends FileStrategy
{
    @Override
    public ForgeModList mergeModLists(ForgeModList old, List<Mod> newMods, Predicate<Mod> acceptor)
    {
        if (installation.mods == null)
        {
            return super.mergeModLists(old, newMods, acceptor);
        }
        log.info("Mod List Merge Strategy: Remove old mods, add new ones");
        List<String> oldDeps = installation.mods.stream()
                .filter(acceptor)
                .map(Mod::dependencyString)
                .collect(Collectors.toList());
        List<String> newDeps = newMods.stream()
                .filter(acceptor)
                .map(Mod::dependencyString)
                .collect(Collectors.toList());
        old.modRef.removeIf(oldDeps::contains);
        old.modRef.addAll(newDeps);
        return old;
    }

    @Override
    public boolean validateOldChecksums(File directory)
    {
        if (installation.overrides == null)
        {
            return true;
        }
        boolean success = true;
        for (FileOverride override : installation.overrides)
        {
            File overrideFile = new File(directory, override.path);
            try
            {
                if (!overrideFile.exists())
                {
                    log.log(config.failDiscrepancies ? Level.ERROR : Level.WARN, "Expected file '{}' did not exit, can't calculate checksum!", overrideFile.getAbsolutePath());
                    success = !config.failDiscrepancies;
                    continue;
                }
                InputStream digestStream = new FileInputStream(overrideFile);
                String checksum = DigestUtils.md5Hex(digestStream);
                digestStream.close();
                if (!Objects.equals(checksum, override.checksum))
                {
                    log.log(config.failDiscrepancies ? Level.ERROR : Level.WARN, "Found discrepancies between existing file '{}' and it's last known checksum!", overrideFile.getAbsolutePath());
                    log.log(config.failDiscrepancies ? Level.ERROR : Level.WARN, " - Stored Checksum: {}", override.checksum);
                    log.log(config.failDiscrepancies ? Level.ERROR : Level.WARN, " - Calculated Checksum: {}", checksum);
                    success = !config.failDiscrepancies;
                }
            }
            catch (IOException e)
            {
                log.log(config.failDiscrepancies ? Level.ERROR : Level.WARN, new FormattedMessageFactory().newMessage("Failed to calculate checksum of file {}!", overrideFile.getAbsolutePath()), e);
                success = !config.failDiscrepancies;
            }
        }
        return success;
    }
}
