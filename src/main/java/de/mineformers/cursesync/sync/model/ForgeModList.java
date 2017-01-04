package de.mineformers.cursesync.sync.model;

import javax.annotation.Nullable;
import java.util.List;

public class ForgeModList
{
    public final String repositoryRoot;
    public final List<String> modRef;
    @Nullable
    public String parentList;

    public ForgeModList(String repositoryRoot, List<String> modRef, @Nullable String parentList)
    {
        this.repositoryRoot = repositoryRoot;
        this.modRef = modRef;
        this.parentList = parentList;
    }
}
