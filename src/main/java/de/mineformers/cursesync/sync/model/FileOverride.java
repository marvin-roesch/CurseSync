package de.mineformers.cursesync.sync.model;

import com.google.common.base.MoreObjects;

public class FileOverride
{
    public final String path;
    public final String checksum;

    public FileOverride(String path, String checksum)
    {
        this.path = path;
        this.checksum = checksum;
    }

    @Override
    public String toString()
    {
        return MoreObjects.toStringHelper(this)
                .add("path", path)
                .add("checksum", checksum)
                .toString();
    }
}
