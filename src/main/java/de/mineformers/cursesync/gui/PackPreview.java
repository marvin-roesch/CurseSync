package de.mineformers.cursesync.gui;

import de.mineformers.cursesync.sync.CurseAPI;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;

import java.io.IOException;

public class PackPreview extends GridPane
{
    private static FXMLLoader loader = new FXMLLoader(PackPreview.class.getResource("/pack_preview.fxml"));
    @FXML
    private Label lblName;
    @FXML
    private Label lblDescription;
    @FXML
    private Label lblLastUpdate;
    @FXML
    private ImageView imgThumbnail;

    public PackPreview()
    {
        getStyleClass().add("pack-preview");
        loader.setRoot(this);
        loader.setController(this);
        try
        {
            loader.load();
        }
        catch (IOException e)
        {
            throw new RuntimeException("Failed to load PackPreview from FXML.", e);
        }
        this.setOnMouseClicked(event ->
        {
            onActionProperty().get().handle(new ActionEvent());
        });
    }

    public PackPreview(CurseAPI.SearchResult search)
    {
        this();
        setName(search.name);
        setDescription(search.description);
        setLastUpdate(search.lastUpdated);
        setThumbnail(new Image(search.thumbnail));
    }

    public String getName()
    {
        return nameProperty().get();
    }

    public void setName(String name)
    {
        nameProperty().set(name);
    }

    public StringProperty nameProperty()
    {
        return lblName.textProperty();
    }

    public String getDescription()
    {
        return nameProperty().get();
    }

    public void setDescription(String description)
    {
        descriptionProperty().set(description);
    }

    public StringProperty descriptionProperty()
    {
        return lblDescription.textProperty();
    }

    public String getLastUpdate()
    {
        return lastUpdateProperty().get();
    }

    public void setLastUpdate(String lastUpdate)
    {
        lastUpdateProperty().set(lastUpdate);
    }

    public StringProperty lastUpdateProperty()
    {
        return lblLastUpdate.textProperty();
    }

    public Image getThumbnail()
    {
        return thumbnailProperty().get();
    }

    public void setThumbnail(Image thumbnail)
    {
        thumbnailProperty().set(thumbnail);
    }

    public ObjectProperty<Image> thumbnailProperty()
    {
        return imgThumbnail.imageProperty();
    }


    // notice we use MouseEvent here only because you call from onMouseEvent, you can substitute any type you need
    private ObjectProperty<EventHandler<ActionEvent>> propertyOnAction = new SimpleObjectProperty<>();

    public final ObjectProperty<EventHandler<ActionEvent>> onActionProperty()
    {
        return propertyOnAction;
    }

    public final void setOnAction(EventHandler<ActionEvent> handler)
    {
        propertyOnAction.set(handler);
    }

    public final EventHandler<ActionEvent> getOnAction()
    {
        return propertyOnAction.get();

    }
}
