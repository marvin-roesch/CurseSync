CurseSync
=========

CurseSync is an application which enables Curse modpack downloading capabilities without the [Curse](https://curse.com) client.

Features
--------
CurseSync currently provides the following features:
 - Download modpacks directly from Curse into a server or client installation
 - Automatically install the required Forge version on servers
 - Update modpacks from any version to new ones (including new MC/Forge versions)
 - Different installation modes and fast failure to keep your installation safe
 - Universal mod repository and caching: Given the same instance of CurseSync, the same mod version will never be downloaded twice, but instead shared across pack installations

Usage
-----
Currently, there only is a command line interface for the application, but it is fully functional.

General usage is as follows:
```
java -jar cursesync-<version>.jar <options>
```

The following command line options are available, all paths may be relative to the running directory of the application:

| Option               | Functionality |
| -------------------- | ------------- |
| `-h`                 | Displays the help page.                                                      |
| `--config <path>`    | An optional configuration file, if specified, other options may be ommitted. Defaults to `./cursesync-config.json`.<br>**Note**: Command line options will take precedence over configuration. |
| `--tmp <path>`       | The directory to use for storing all 'temporary' files, this includes downloaded mods by default.<br>Defaults to `./tmp`. |
| `--output <path>`    | The directory to install the modpack into.<br>**Note**: This should be different from the running directory, otherwise you might run into issues depending on the installation mode. |
| `--mode <install|update|overwrite>` | Tells the application how to deal with existing installations. The default value is `update`.<br>The different values mean the following: <ul><li>`install`: Will only install the modpack into the output directory if there is no previous installation there.</li><li>`update`: Will install the modpack into the output directory if there is previous installation there, otherwise it will attempt to update the existing installation to the specified version.</li><li>`overwrite`: Will always freshly install the modpack into the output directory ignoring the contents of the output directory.<br>**Note**: The output directory will be completely wiped before installation!</li></ul> |
| `--fail-discrepancies` | If the application is in `update` mode and there are changes in files within the installation detected, this option will make the application fail rather than simply warning the user about them. This option is *off* by default, considering that existing instances should be manually backed up before updating a modpack. |
| **Installation specific properties** | |
| `--server`           | Makes the application install a server rather than a client.<br>Currently the only difference is that a Forge server will be automatically installed in server mode. |
| `--project <curse-slug>`  | The slug of the Curse project which represents the modpack to install. The slug can be taken from the Curse project's URL (**Note**: The slugs from CurseForge will not work!). They generally take the form `<numeric-id>-<project-name>`. |
| `--game <mc-version>`  | The version of minecraft the desired pack version is designed for. |
| `--version <version>`  | The version of the pack to download.<br>**Note**: Version numbers from the Curse client won't necessarily work for this option, since the version is checked against the file name on Curse, so take whatever part of the filename is unique to the version! |

**Note** that there are alternative forms of most options, consult the app's help for further details.

### Configuration File
The following is an example for a full configuration JSON:
```
{
  "projectSlug": "253632-ftb-presents-direwolf20-1-10",
  "gameVersion": "1.10.2",
  "projectVersion": "1.2.2",
  "output": "./instance",
  "mode": "update",
  "server": true,
  "tmpDirectory": "./tmp",
  "failDiscrepancies": false
}
```


Planned Features
----------------
Considering the required functionality of the application is done, most planned features are merely for convenience or ease of use:
 - A graphical user interface with search capabilities is already in the making, is not currently functional, though
 - Automatic backups of old files
 - Better cache validity checks (use checksums for pack files, might require change in Curse API)