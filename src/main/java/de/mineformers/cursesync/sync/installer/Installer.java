package de.mineformers.cursesync.sync.installer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.gson.JsonParseException;
import com.google.gson.stream.JsonWriter;
import com.google.inject.Inject;
import de.mineformers.cursesync.CurseSync;
import de.mineformers.cursesync.sync.CurseAPI;
import de.mineformers.cursesync.sync.mode.FileStrategy;
import de.mineformers.cursesync.sync.model.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.FormattedMessageFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static de.mineformers.cursesync.sync.installer.InstallStep.Result.*;

public abstract class Installer
{
    @Inject
    protected CurseSync.Configuration config;
    @Inject
    protected Installation installation;
    @Inject
    protected CurseAPI api;
    @Inject
    protected Logger log;
    @Inject
    protected FileStrategy strategy;
    protected CurseProject project;
    protected ProjectVersion version;
    protected PackManifest manifest;
    private List<InstallStep> steps = Lists.newArrayList();

    public void init(@Nonnull CurseProject project)
    {
        this.project = project;
        version = project.versions.get(config.gameVersion).stream().filter(v -> v.name.contains(config.projectVersion != null ? config.projectVersion : "")).findFirst().get();
        steps.add(this::downloadPackFile);
        steps.add(this::unzipPackFile);
        steps.add(this::loadManifest);
        steps.add(this::downloadMods);
        steps.add(this::prepareDirectory);
        steps.addAll(constructSteps());
        steps.add(this::createModList);
        steps.add(this::copyOverrides);
        steps.add(this::saveInstallation);
    }

    protected abstract List<InstallStep> constructSteps();

    protected abstract boolean acceptsMod(Mod mod);

    public boolean execute()
    {
        return strategy.canInstall() && steps.stream().sequential().reduce(SUCCESS, (acc, elem) ->
        {
            if (acc == FAILURE) return FAILURE;
            else if (acc == SKIP_NEXT) return SUCCESS;
            else return elem.execute();
        }, (a, b) -> b) != FAILURE;
    }

    protected InstallStep.Result downloadPackFile()
    {
        log.info("Modpack file is classified as '{}'. Downloading...", version.name);
        String packPath = "modpacks/" + config.projectSlug + "/" + version.id;
        File packDirectory = new File(config.tmpDirectory, packPath);
        if (packDirectory.exists() && packDirectory.isDirectory())
        {
            log.info("Found existing pack directory, assuming equivalence. Skipping download.");
            return SKIP_NEXT;
        }
        try
        {
            URI uri = api.getCFURI("/projects/" + config.projectNameSlug() + "/files/" + version.id + "/download", null);
            boolean downloadResult = api.downloadFile(uri, new File(config.tmpDirectory, packPath + ".zip"), 3);
            return downloadResult ? SUCCESS : FAILURE;
        }
        catch (URISyntaxException e)
        {
            log.error("Failed to parse modpack url, aborting!", e);
            return FAILURE;
        }
    }

    protected InstallStep.Result unzipPackFile()
    {
        log.info("Unpacking modpack file...");
        String packPath = "modpacks/" + config.projectSlug + "/" + version.id;
        File zipFile = new File(config.tmpDirectory, packPath + ".zip");
        boolean result = unzip(zipFile, new File(config.tmpDirectory, packPath));
        if (result)
        {
            log.info("Pack was sucessfully unpacked, deleting zip file...");
            if (!zipFile.delete())
            {
                log.warn("Failed to delete zip file.");
            }
        }
        return result ? SUCCESS : FAILURE;
    }

    protected InstallStep.Result loadManifest()
    {
        String manifestPath = "modpacks/" + config.projectSlug + "/" + version.id + "/manifest.json";
        try
        {
            log.info("Loading pack manifest file...");
            manifest = CurseSync.GSON.fromJson(new FileReader(new File(config.tmpDirectory, manifestPath)), PackManifest.class);
            log.debug("Successfully loaded pack manifest: {}", manifest);
            return SUCCESS;
        }
        catch (JsonParseException e)
        {
            log.error("Failed to parse Pack Manifest JSON. This might be due to a change in the file format.");
            return FAILURE;
        }
        catch (FileNotFoundException e)
        {
            log.error("Pack manifest file 'manifest.json' could not be found in pack files. CurseSync does not support other pack formats!");
            return FAILURE;
        }
    }

    protected InstallStep.Result downloadMods()
    {
        log.info("Mod repository is located at '{}'.", installation.modRepository.getAbsolutePath());
        log.info("Downloading required mod files to repository...");
        List<Integer> failingIds = Lists.newArrayList();
        for (Mod mod : manifest.mods)
        {
            if (!acceptsMod(mod))
            {
                log.info("Mod with id {}, version {} is not required on this side, skipping file...", mod.projectId, mod.fileId);
                continue;
            }
            File modPath = new File(installation.modRepository.getAbsolutePath() + "/" + mod.artifactPath("jar"));
            if (modPath.exists())
            {
                log.info("Mod with id {}, version {} was already downloaded, skipping file...", mod.projectId, mod.fileId);
                continue;
            }
            String slug = api.getModSlug(mod.projectId);
            if (slug == null)
            {
                log.error("Could not get slug for project id {}, skipping file...", mod.projectId);
                failingIds.add(mod.projectId);
                continue;
            }
            log.info("Downloading file {} for mod {} (id: {})", mod.fileId, slug, mod.projectId);
            try
            {
                if (!api.downloadFile(api.getCFURI("/projects/" + slug + "/files/" + mod.fileId + "/download", null), modPath, 3))
                {
                    failingIds.add(mod.projectId);
                }
            }
            catch (URISyntaxException e)
            {
                log.error("Could not parse download url, skipping file...");
                failingIds.add(mod.projectId);
            }
        }
        if (!failingIds.isEmpty())
        {
            log.error("Not all mods were successfully downloaded, ");
            return FAILURE;
        }
        return SUCCESS;
    }

    protected InstallStep.Result prepareDirectory()
    {
        log.info("Starting installation, preparing output directory...");
        return strategy.prepareDirectory() ? SUCCESS : FAILURE;
    }

    protected InstallStep.Result createModList()
    {
        log.info("Adding mods to mod list file...");
        File base = new File(config.output, "mods/mod_list.json");
        if (base.exists())
        {
            log.info("There already is a mod list in the mods directory, adding new data...");
            return addModList(base) ? SUCCESS : FAILURE;
        }
        log.info("Creating new mod list in mods directory...");
        ForgeModList modList = generateModList();
        return writeModList(base, modList) ? SUCCESS : FAILURE;
    }

    private boolean addModList(File file)
    {
        try
        {
            ForgeModList list = CurseSync.GSON.fromJson(new FileReader(file), ForgeModList.class);
            if (list == null)
            {
                log.error("Failed to parse existing mod list file '{}', aborting!", file.getAbsolutePath());
                return false;
            }
            if (Objects.equals(list.repositoryRoot, installation.modRepository.getAbsolutePath()))
            {
                log.info("Found mod list file with correct repository root, merging mods...");
                ForgeModList merged = strategy.mergeModLists(list, manifest.mods, this::acceptsMod);
                return writeModList(file, merged);
            }
            else if (list.parentList == null)
            {
                log.info("Found mod list with different repository root but without parent, adding parent...");
                File parent = new File(config.output, "mods/mod_list_pack.json");
                list.parentList = "absolute:" + parent.getAbsolutePath();
                return writeModList(parent, generateModList()) && writeModList(file, list);
            }
            else
            {
                log.info("Found mod list file with different repository root and parent file, analyzing parent file...");
                String parent = list.parentList;
                File parentFile = parent.startsWith("absolute:") ? new File(parent.substring(9)) : new File(config.output, parent);
                return addModList(parentFile);
            }
        }
        catch (JsonParseException e)
        {
            log.error(new FormattedMessageFactory().newMessage("Existing mod list file '{}' contained malformed JSON, aborting!", file.getAbsolutePath()), e);
            return false;
        }
        catch (FileNotFoundException e)
        {
            log.error("Could not find mod list file '{}'.", file.getAbsolutePath());
            return false;
        }
    }

    private boolean writeModList(File file, ForgeModList modList)
    {
        try
        {
            log.info("Writing mod list to '{}'...", file.getAbsolutePath());
            File parent = file.getParentFile();
            if (!parent.exists() && !parent.mkdirs())
            {
                log.error("Failed to create required directories for file '{}', aborting!", file.getAbsolutePath());
                return false;
            }
            BufferedWriter writer = new BufferedWriter(new FileWriter(file));
            JsonWriter jsonWriter = new JsonWriter(writer);
            jsonWriter.setIndent("  ");
            CurseSync.GSON.toJson(modList, ForgeModList.class, jsonWriter);
            writer.close();
        }
        catch (IOException e)
        {
            log.error(new FormattedMessageFactory().newMessage("Failed to write Mod List JSON '{}', aborting!", file.getAbsolutePath()), e);
            return false;
        }
        return true;
    }

    @Nonnull
    private ForgeModList generateModList()
    {
        List<String> modRefs = manifest.mods.stream()
                .filter(this::acceptsMod)
                .map(Mod::dependencyString)
                .collect(Collectors.toList());
        return new ForgeModList(installation.modRepository.getAbsolutePath(), modRefs, null);
    }

    protected InstallStep.Result copyOverrides()
    {
        log.info("Copying override files...");
        String overridesPath = "modpacks/" + config.projectSlug + "/" + version.id + "/" + manifest.overridesPath + "/";
        File srcDirectory = new File(config.tmpDirectory, overridesPath);
        File destDirectory = config.output;
        if (destDirectory == null)
        {
            log.error("Output directory unexpectedly was null!");
            return FAILURE;
        }
        log.info("Calculating override checksums...");
        List<FileOverride> checksums = getChecksums(srcDirectory, srcDirectory);
        if (checksums == null)
        {
            log.error("Failed to gather all checksums, aborting!");
            return FAILURE;
        }
        log.info("Found {} override files and calculated their checksums!", checksums.size());
        log.info("Checking old checksums for discrepancies...");
        if (!strategy.validateOldChecksums(destDirectory))
        {
            log.log(config.failDiscrepancies ? Level.ERROR : Level.WARN, "There appear to be discrepancies between the installation's last known state and the actual data.");
            if (config.failDiscrepancies)
            {
                log.error("Please fix the listed discrepancies manually!");
                return FAILURE;
            }
        }
        log.info("All checksums appear to be valid, deleting old files now...");
        if (!strategy.deleteOldOverrides(destDirectory, checksums))
        {
            log.error("Some files could not be deleted, please do so manually!");
            return FAILURE;
        }
        log.info("Copying new overrides...");
        try
        {
            FileUtils.copyDirectory(srcDirectory, destDirectory, file ->
            {
                String relative = srcDirectory.toPath().relativize(Paths.get(file.getAbsolutePath())).toString();
                return file.isDirectory() || !new File(destDirectory, relative).exists();
            });
        }
        catch (IOException e)
        {
            log.error("Failed to copy overrides to output directory, aborting!", e);
            return FAILURE;
        }
        log.info("Done!");
        installation.overrides = checksums;
        return SUCCESS;
    }

    @Nullable
    private List<FileOverride> getChecksums(File relative, File directory)
    {
        ImmutableList.Builder<FileOverride> checksums = new ImmutableList.Builder<>();
        File[] files = directory.listFiles();
        if (files != null)
        {
            for (File f : files)
            {
                if (f.isDirectory())
                {
                    List<FileOverride> subResult = getChecksums(relative, f);
                    if (subResult == null)
                        return null;
                    checksums.addAll(subResult);
                }
                else
                {
                    try
                    {
                        InputStream digestStream = new FileInputStream(f);
                        String checksum = DigestUtils.md5Hex(digestStream);
                        digestStream.close();
                        checksums.add(new FileOverride(relative.toPath().relativize(f.toPath()).toString(), checksum));
                    }
                    catch (IOException e)
                    {
                        log.error(new FormattedMessageFactory().newMessage("Failed to calculate checksum of file {}!", f.getAbsolutePath()), e);
                        return null;
                    }
                }
            }
        }
        return checksums.build();
    }

    protected InstallStep.Result saveInstallation()
    {
        Installation newInstallation = new Installation(config.projectId(), config.projectNameSlug(), config.gameVersion, config.server, installation.modRepository, installation.forgeVersion, config.projectVersion, manifest.mods, installation.overrides);
        File installationFile = config.installationFile();
        try
        {
            log.info("Saving installation data to '{}'...", installationFile.getAbsolutePath());
            File parent = installationFile.getParentFile();
            if (!parent.exists() && !parent.mkdirs())
            {
                log.error("Failed to create required directories for installation file, aborting!");
                return FAILURE;
            }
            BufferedWriter writer = new BufferedWriter(new FileWriter(installationFile));
            JsonWriter jsonWriter = new JsonWriter(writer);
            jsonWriter.setIndent("  ");
            CurseSync.GSON.toJson(newInstallation, Installation.class, jsonWriter);
            writer.close();
        }
        catch (IOException e)
        {
            log.error("Failed to write installation file, aborting!", e);
            return FAILURE;
        }
        return SUCCESS;
    }

    private boolean unzip(File zipFile, File folder)
    {
        if (folder.exists())
        {
            log.info("Pack folder already exists, assuming equivalence. Skipping unpacking.");
            return true;
        }
        byte[] buffer = new byte[1024];

        try
        {
            //create output directory is not exists
            if (!folder.exists() && !folder.mkdirs())
            {
                log.error("Failed to created required directories.");
                return false;
            }

            //get the zip file content
            ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile));
            //get the zipped file list entry
            ZipEntry ze = zis.getNextEntry();

            while (ze != null)
            {
                String fileName = ze.getName();
                File newFile = new File(folder, fileName);
                log.debug("Unpacking: {} '{}'", ze.isDirectory() ? "Directory" : "File", fileName);
                if (!ze.isDirectory())
                {
                    File parent = newFile.getParentFile();
                    if (!parent.exists() && !parent.mkdirs())
                    {
                        log.error("Failed to created required directories for file '{}'.", newFile.getAbsolutePath());
                        return false;
                    }
                    FileOutputStream fos = new FileOutputStream(newFile);

                    int len;
                    while ((len = zis.read(buffer)) > 0)
                    {
                        fos.write(buffer, 0, len);
                    }

                    fos.close();
                }
                else if (!newFile.exists() && !newFile.mkdirs())
                {
                    log.error("Failed to created directory '{}'.", newFile.getAbsolutePath());
                    return false;
                }
                ze = zis.getNextEntry();
            }

            zis.closeEntry();
            zis.close();
            return true;
        }
        catch (IOException e)
        {
            log.error("Failed to unpack modpack file, aborting!", e);
            return false;
        }
    }
}
