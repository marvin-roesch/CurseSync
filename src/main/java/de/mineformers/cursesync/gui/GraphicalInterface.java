package de.mineformers.cursesync.gui;

import com.google.inject.Inject;
import de.mineformers.cursesync.CurseSync;
import de.mineformers.cursesync.CurseSyncInterface;
import de.mineformers.cursesync.sync.CurseAPI;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

public class GraphicalInterface extends Application implements CurseSyncInterface
{
    @Nonnull
    private final Logger log = LogManager.getLogger("CurseSync");
    private final Executor uiExecutor = Platform::runLater;
    @Inject
    private CurseSync app;
    @Inject
    private CurseAPI api;
    @Inject
    private ExecutorService executor;
    @Inject
    private CurseSync.Configuration config;
    @Inject
    private static FXMLLoader loader;
    private static EventHandler<WindowEvent> closeRequestHandler;

    @Nonnull
    @Override
    public Logger log()
    {
        return log;
    }

    @Nonnull
    @Override
    public Executor uiExecutor()
    {
        return uiExecutor;
    }

    @Override
    public void run()
    {
        log.info("===============================================================");
        log.info("Starting CurseSync GUI Client with the following configuration:");
        config.dump(log, Level.INFO);
        log.info("===============================================================");
        api.init();
        closeRequestHandler = event ->
        {
            app.shutdownExecutor();
            Platform.exit();
            System.exit(0);
        };
        launch();
    }

    @Override
    public void start(Stage stage) throws Exception
    {
        stage.setOnCloseRequest(closeRequestHandler);
        loader.setLocation(getClass().getResource("/main.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root, 500, 400);
        scene.getStylesheets().add("styles.css");
        stage.setTitle("CurseSync v" + CurseSync.VERSION);
        stage.setScene(scene);
        stage.show();
    }
}
