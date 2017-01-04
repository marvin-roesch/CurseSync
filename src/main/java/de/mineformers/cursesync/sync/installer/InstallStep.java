package de.mineformers.cursesync.sync.installer;

public interface InstallStep
{
    Result execute();

    enum Result
    {
        SUCCESS, SKIP_NEXT, FAILURE
    }
}
