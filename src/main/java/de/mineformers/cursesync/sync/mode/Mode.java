package de.mineformers.cursesync.sync.mode;

import com.google.gson.annotations.SerializedName;

public enum Mode
{
    @SerializedName("install")
    INSTALL(new InstallStrategy()),
    @SerializedName("overwrite")
    INSTALL_OVERWRITE(new OverwriteStrategy()),
    @SerializedName("update")
    UPDATE(new UpdateStrategy());

    public final FileStrategy strategy;

    Mode(FileStrategy strategy)
    {
        this.strategy = strategy;
    }

    @Override
    public String toString()
    {
        return name().toLowerCase();
    }
}
