package de.mineformers.cursesync.sync.model;

import com.google.common.base.MoreObjects;
import com.google.gson.annotations.SerializedName;

public class Mod
{
    @SerializedName("projectID")
    public final int projectId;
    @SerializedName("fileID")
    public final int fileId;
    public final boolean required;
    public boolean clientOnly = false;
    public boolean serverOnly = false;

    public Mod(int projectId, int fileId, boolean required)
    {
        this.projectId = projectId;
        this.fileId = fileId;
        this.required = required;
    }

    public String artifactPath(String extension)
    {
        return "mc/" + artifactName() + "/" + fileId + "/" + artifactName() + "-" + fileId + "." + extension;
    }

    public String dependencyString()
    {
        return "mc:" + artifactName() + ":" + fileId;
    }

    public String artifactName()
    {
        return "mod" + projectId;
    }

    @Override
    public String toString()
    {
        return MoreObjects.toStringHelper(this)
                .add("projectId", projectId)
                .add("fileId", fileId)
                .add("required", required)
                .add("clientOnly", clientOnly)
                .add("serverOnly", serverOnly)
                .toString();
    }
}
