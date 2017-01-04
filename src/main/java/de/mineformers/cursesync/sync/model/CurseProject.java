package de.mineformers.cursesync.sync.model;

import com.google.common.base.MoreObjects;
import com.google.common.collect.Multimap;
import com.google.gson.annotations.SerializedName;

import java.time.OffsetDateTime;
import java.util.List;

public class CurseProject
{
    public final String title;
    public final String category;
    public final String url;
    public final String thumbnail;
    public final List<String> authors;
    @SerializedName("updated_at")
    public final OffsetDateTime updated;
    @SerializedName("created_at")
    public final OffsetDateTime created;
    public final Multimap<String, ProjectVersion> versions;

    public CurseProject(String title, String category, String url, String thumbnail, List<String> authors, OffsetDateTime updated, OffsetDateTime created, Multimap<String, ProjectVersion> versions)
    {
        this.title = title;
        this.category = category;
        this.url = url;
        this.thumbnail = thumbnail;
        this.authors = authors;
        this.updated = updated;
        this.created = created;
        this.versions = versions;
    }

    @Override
    public String toString()
    {
        return MoreObjects.toStringHelper(this)
                .add("title", title)
                .add("category", category)
                .add("url", url)
                .add("thumbnail", thumbnail)
                .add("authors", authors)
                .add("updated", updated)
                .add("created", created)
                .add("versions", versions)
                .toString();
    }
}
