package de.mineformers.cursesync.sync.model;

import com.google.common.base.MoreObjects;
import com.google.gson.annotations.SerializedName;

import java.util.List;

public class PackManifest
{
    @SerializedName("minecraft")
    public final GameInfo gameInfo;
    @SerializedName("files")
    public final List<Mod> mods;
    @SerializedName("overrides")
    public final String overridesPath;

    public PackManifest(GameInfo gameInfo, List<Mod> mods, String overridesPath)
    {
        this.gameInfo = gameInfo;
        this.mods = mods;
        this.overridesPath = overridesPath;
    }

    @Override
    public String toString()
    {
        return MoreObjects.toStringHelper(this)
                .add("gameInfo", gameInfo)
                .add("mods", mods)
                .add("overridesPath", overridesPath)
                .toString();
    }

    public static class GameInfo
    {
        public final String version;
        public final List<ModLoader> modLoaders;

        public GameInfo(String version, List<ModLoader> modLoaders)
        {
            this.version = version;
            this.modLoaders = modLoaders;
        }

        @Override
        public String toString()
        {
            return MoreObjects.toStringHelper(this)
                    .add("version", version)
                    .add("modLoaders", modLoaders)
                    .toString();
        }
    }

    public static class ModLoader
    {
        public final String id;
        public final boolean primary;

        public ModLoader(String id, boolean primary)
        {
            this.id = id;
            this.primary = primary;
        }

        @Override
        public String toString()
        {
            return MoreObjects.toStringHelper(this)
                    .add("id", id)
                    .add("primary", primary)
                    .toString();
        }
    }
}
