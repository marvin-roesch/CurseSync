package de.mineformers.cursesync.sync.model;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.List;

public class Installation
{
    public final int projectId;
    @Nonnull
    public final String projectNameSlug;
    @Nonnull
    public final String projectSlug;
    @Nonnull
    public final String gameVersion;
    public final boolean server;
    @Nonnull
    public File modRepository;
    @Nullable
    public String lastFile;
    @Nullable
    public String forgeVersion;
    @Nullable
    public List<Mod> mods;
    @Nullable
    public List<FileOverride> overrides;

    public Installation(int projectId, @Nonnull String projectNameSlug, @Nonnull String gameVersion, boolean server, @Nonnull File modRepository, @Nullable String lastFile, @Nullable String forgeVersion, @Nullable List<Mod> mods, @Nullable List<FileOverride> overrides)
    {
        this.projectId = projectId;
        this.projectNameSlug = projectNameSlug;
        this.projectSlug = projectId + "-" + projectNameSlug;
        this.gameVersion = gameVersion;
        this.server = server;
        this.modRepository = modRepository;
        this.forgeVersion = forgeVersion;
        this.lastFile = lastFile;
        this.mods = mods;
        this.overrides = overrides;
    }
}
