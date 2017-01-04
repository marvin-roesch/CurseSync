package de.mineformers.cursesync.gui;

import com.google.common.base.Joiner;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import de.mineformers.cursesync.sync.CurseAPI;
import de.mineformers.cursesync.sync.model.CurseProject;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.Logger;
import org.controlsfx.control.PopOver;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

public class MainController
{
    @Inject
    private CurseAPI api;
    @Inject
    private Logger log;
    @Inject
    @Named("UI")
    private Executor uiExecutor;
    @Inject
    private ExecutorService executor;
    public TextField txtSearch;
    public ImageView imgThumbnail;
    public Label lblPackName;
    public Label lblPackAuthors;
    public Label lblLastUpdate;
    public Label lblGameVersions;
    public ToggleGroup tglInstallationType;
    private PopOver searchPopOver;
    private VBox packList;
    private CurseProject activePack;
    private CompletableFuture<Void> currentSearch;
    private static final Comparator<String> VERSION_COMPARATOR = (first, second) ->
    {
        String[] a = first.split("\\.");
        String[] b = second.split("\\.");
        int length = a.length;
        if (b.length > a.length) length = b.length;

        for (int i = 0; i < length; i++)
        {
            String s0 = null;
            if (i < a.length) s0 = a[i];
            Integer i0 = (s0 == null) ? 0 : Integer.parseInt(s0);
            String s1 = null;
            if (i < b.length) s1 = b[i];
            Integer i1 = (s1 == null) ? 0 : Integer.parseInt(s1);
            if (i0.compareTo(i1) < 0) return -1;
            else if (i1.compareTo(i0) < 0) return 1;
        }
        return 0;
    };

    public void initialize()
    {
        packList = new VBox();
        packList.setMinWidth(400);
        packList.setPrefWidth(400);
        packList.setMaxWidth(400);
        packList.setAlignment(Pos.CENTER);
        searchPopOver = new PopOver(packList);
        searchPopOver.setArrowLocation(PopOver.ArrowLocation.TOP_CENTER);
        searchPopOver.setAutoHide(false);
        txtSearch.textProperty().addListener((observable, oldValue, newValue) -> search());
        txtSearch.focusedProperty().addListener((observable, oldValue, newValue) ->
        {
            if (newValue)
            {
                search();
            }
            else
            {
                searchPopOver.hide();
            }
        });
    }

    private void loadPack(String slug)
    {
        CompletableFuture
                .supplyAsync(() -> api.getModpack(slug), executor)
                .thenAcceptAsync(result ->
                {
                    Joiner commaJoiner = Joiner.on(",");
                    imgThumbnail.setImage(new Image(result.thumbnail));
                    lblPackName.setText(result.title);
                    lblPackAuthors.setText(String.format("by %s", commaJoiner.join(result.authors)));
                    lblGameVersions.setText(commaJoiner.join(result.versions.keySet().stream().sorted(VERSION_COMPARATOR).toArray()));
                    lblLastUpdate.setText(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(result.updated));
                }, uiExecutor);
    }

    private void search()
    {
        if (currentSearch != null)
            currentSearch.cancel(true);
        currentSearch = CompletableFuture
                .supplyAsync(() -> api.search("modpacks", txtSearch.getText(), 3), executor)
                .thenAcceptAsync(result ->
                {
                    packList.getChildren().clear();
                    if (!result.isEmpty())
                        result.stream().map(search ->
                        {
                            PackPreview preview = new PackPreview(search);
                            preview.setOnAction(event ->
                            {
                                txtSearch.getParent().requestFocus();
                                loadPack(search.slug);
                            });
                            return preview;
                        }).forEach(packList.getChildren()::add);
                    else
                        packList.getChildren().add(new Label("No results found."));
                    searchPopOver.show(txtSearch);
                }, uiExecutor);
    }
}
