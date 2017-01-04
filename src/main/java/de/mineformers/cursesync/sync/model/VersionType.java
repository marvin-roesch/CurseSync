package de.mineformers.cursesync.sync.model;

import com.google.gson.annotations.SerializedName;

public enum VersionType
{
    @SerializedName("release")
    RELEASE,
    @SerializedName("beta")
    BETA,
    @SerializedName("alpha")
    ALPHA;

    @Override
    public String toString()
    {
        return name().toLowerCase();
    }
}
