package de.panbytes.dexter.appfx.scene.control;

import java.util.Arrays;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.VBox;

/**
 * @author Daniel Hári
 * @author Fabian Krippendorff
 * @see <a href="https://stackoverflow.com/a/44314455">Daniel Hári's source this solution is based on.</a>
 */
public class ZoomableScrollPane extends ScrollPane {

    private final double scrollZoomIntensity = 0.05;
    private final double keyZoomIntensity = 1.2;
    private final DoubleProperty scale = new SimpleDoubleProperty(1.0);
    private Node target;
    private Node zoomNode;

    public ZoomableScrollPane(Node target) {
        super();
        this.target = target;
        this.zoomNode = new Group(target);
        setContent(outerNode(zoomNode));

        initListeners();

        target.scaleXProperty().bind(scale);
        target.scaleYProperty().bind(scale);

        setPannable(true);

        setFitToHeight(true); // fit content to viewport => center
        setFitToWidth(true); // fit content to viewport => center

        setFocusTraversable(true);
    }

    public double getScale() {
        return scale.get();
    }

    public void setScale(double scale) {
        this.scale.set(scale);
    }

    public DoubleProperty scaleProperty() {
        return scale;
    }


    /**
     * Scale content to fit the viewport's bounds. Only possible if component's bounds are positive => Components have to be visible. If shrinkOnly is set,
     * nothing happens if content is smaller.
     *
     * @param shrinkOnly if true, only shrink larger content, but dont enlarge smaller content.
     * @return the scale factor or 0 if operation wasn't possible
     */
    public double scaleToFitContent(boolean shrinkOnly) {
        double fitHeightScale = getViewportBounds().getHeight() / target.getBoundsInLocal().getHeight();
        double fitWidthScale = getViewportBounds().getWidth() / target.getBoundsInLocal().getWidth();
        double newScale = Math.min(Math.min(fitHeightScale, fitWidthScale), shrinkOnly ? 1 : Double.MAX_VALUE);
        if (newScale > 0) {
            scale.set(newScale);
        }
        return newScale;
    }

    private void initListeners() {
        this.getContent().setOnScroll(scrollEvent -> {
            if (scrollEvent.isControlDown()) {
                scrollEvent.consume();
                onScroll(scrollEvent);
            }
        });
        this.sceneProperty().addListener((observable, old, scene) -> {
            // wait for scene to be not null!
            if (scene != null) {
                // register to scene for key-events and ensure the event's target to be a parent of this.
                scene.addEventHandler(KeyEvent.KEY_PRESSED, keyEvent -> {
                    Parent checkSource = this;
                    do {
                        if (checkSource == keyEvent.getTarget() && Arrays.asList(KeyCode.ADD, KeyCode.PLUS, KeyCode.SUBTRACT, KeyCode.MINUS)
                                                                         .contains(keyEvent.getCode())) {
                            keyEvent.consume();
                            onZoomKey(keyEvent);
                        }
                    } while ((checkSource = checkSource.getParent()) != null);
                });
            }
        });
    }

    private Node outerNode(Node node) {
        VBox vBox = new VBox(node);
        vBox.setAlignment(Pos.CENTER);
        return vBox;
    }

    private void onScroll(ScrollEvent scrollEvent) {
        Point2D mousePoint = new Point2D(scrollEvent.getX(), scrollEvent.getY());
        double zoomFactor = Math.exp(scrollEvent.getTextDeltaY() * scrollZoomIntensity);

        doZoom(mousePoint, zoomFactor);
    }

    private void onZoomKey(KeyEvent keyEvent) {

        Bounds viewportInScene = this.localToScene(this.getViewportBounds());
        double centerX = 0.5 * viewportInScene.getWidth();
        double centerY = 0.5 * viewportInScene.getHeight();

        Point2D zoomPointInContent = this.getContent().sceneToLocal(new Point2D(centerX, centerY));

        switch (keyEvent.getCode()) {
            case ADD:
            case PLUS:
                doZoom(zoomPointInContent, keyZoomIntensity);
                break;
            case SUBTRACT:
            case MINUS:
                doZoom(zoomPointInContent, 1 / keyZoomIntensity);
                break;
        }
    }

    /**
     * perform zooming
     *
     * @param zoomFixPoint the point to zoom into; relative to scrollpane's content node.
     * @param zoomFactor zf > 1: zoom in;  zf < 1 zoom out
     */
    private void doZoom(Point2D zoomFixPoint, double zoomFactor) {
        Bounds innerBounds = zoomNode.getLayoutBounds();
        Bounds viewportBounds = getViewportBounds();

        // calculate pixel offsets from [0, 1] range
        double valX = this.getHvalue() * (innerBounds.getWidth() - viewportBounds.getWidth());
        double valY = this.getVvalue() * (innerBounds.getHeight() - viewportBounds.getHeight());

        scale.set(scale.get() * zoomFactor);
        this.layout(); // refresh ScrollPane scroll positions & target bounds

        // convert target coordinates to zoomTarget coordinates
        Point2D posInZoomTarget = target.parentToLocal(zoomNode.parentToLocal(zoomFixPoint));

        // calculate adjustment of scroll position (pixels)
        Point2D adjustment = target.getLocalToParentTransform().deltaTransform(posInZoomTarget.multiply(zoomFactor - 1));

        // convert back to [0, 1] range
        // (too large/small values are automatically corrected by ScrollPane)
        Bounds updatedInnerBounds = zoomNode.getBoundsInLocal();
        this.setHvalue((valX + adjustment.getX()) / (updatedInnerBounds.getWidth() - viewportBounds.getWidth()));
        this.setVvalue((valY + adjustment.getY()) / (updatedInnerBounds.getHeight() - viewportBounds.getHeight()));
    }
}