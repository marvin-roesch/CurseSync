package de.mineformers.cursesync.sync.model;

import com.google.common.base.MoreObjects;
import com.google.gson.annotations.SerializedName;

import java.time.OffsetDateTime;

public class ProjectVersion
{
    public final int id;
    public final String url;
    public final String name;
    public final VersionType type;
    @SerializedName("created_at")
    public final OffsetDateTime created;

    public ProjectVersion(int id, String url, String name, VersionType type, OffsetDateTime created)
    {
        this.id = id;
        this.url = url;
        this.name = name;
        this.type = type;
        this.created = created;
    }

    @Override
    public String toString()
    {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .add("url", url)
                .add("name", name)
                .add("type", type)
                .add("created", created)
                .toString();
    }
}
