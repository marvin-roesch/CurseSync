package de.mineformers.cursesync.sync.mode;

import java.io.File;

public class OverwriteStrategy extends FileStrategy
{
    @Override
    public boolean prepareDirectory()
    {
        log.info("Clearing output directory...");
        if (config.output == null)
        {
            log.error("Output directory unexpectedly was null, aborting");
            return false;
        }
        if (!deleteFolder(config.output) && !config.output.mkdirs())
        {
            log.error("Failed to completely clear output directory, aborting!");
            return false;
        }
        return true;
    }

    private boolean deleteFolder(File folder)
    {
        boolean success = true;
        File[] files = folder.listFiles();
        if (files != null)
        { //some JVMs return null for empty dirs
            for (File f : files)
            {
                if (f.isDirectory())
                {
                    deleteFolder(f);
                }
                else
                {
                    if (!f.delete())
                    {
                        log.error("Failed to delete file {}!", f.getAbsolutePath());
                        success = false;
                    }
                }
            }
        }
        if (!folder.delete())
        {
            log.error("Failed to delete folder {}!", folder.getAbsolutePath());
            success = false;
        }
        return success;
    }
}
