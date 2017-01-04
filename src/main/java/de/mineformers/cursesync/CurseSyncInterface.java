package de.mineformers.cursesync;

import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.util.concurrent.Executor;

public interface CurseSyncInterface
{
    @Nonnull
    Logger log();

    @Nonnull
    Executor uiExecutor();

    void run();
}
