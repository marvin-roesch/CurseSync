package de.mineformers.cursesync.sync.mode;

public class InstallStrategy extends FileStrategy
{
    @Override
    public boolean canInstall()
    {
        if (installation.lastFile != null)
        {
            log.error("Could not install modpack because there already was an installation in the desired output directory!");
            return false;
        }
        return true;
    }
}
