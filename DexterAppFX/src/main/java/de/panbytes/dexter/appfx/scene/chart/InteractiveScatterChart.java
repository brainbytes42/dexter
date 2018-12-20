package de.panbytes.dexter.appfx.scene.chart;

import javafx.beans.NamedArg;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Point2D;
import javafx.scene.chart.Axis;
import javafx.scene.chart.ScatterChart;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Effect;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polyline;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Fabian Krippendorff
 */
public class InteractiveScatterChart<X extends Number, Y extends Number> extends ScatterChart<X, Y> {

    private final Polyline lasso = new Polyline();

    private final ObservableList<Data<X, Y>> currentSelection = FXCollections.observableArrayList();
    private final ObservableList<Data<X, Y>> currentSelectionUnmodifiable = FXCollections.unmodifiableObservableList(currentSelection);

    private final ObjectProperty<Effect> selectionEffect = new SimpleObjectProperty<>(
            new DropShadow(BlurType.TWO_PASS_BOX, Color.RED, 15, 0.5, 0, 0));

    /**
     * @see ScatterChart#ScatterChart(Axis, Axis)
     */
    public InteractiveScatterChart(@NamedArg("xAxis") final Axis<X> xAxis, @NamedArg("yAxis") final Axis<Y> yAxis) {
        this(xAxis, yAxis, FXCollections.observableArrayList());
    }

    /**
     * @see ScatterChart#ScatterChart(Axis, Axis, ObservableList)
     */
    public InteractiveScatterChart(@NamedArg("xAxis") final Axis<X> xAxis,
                                   @NamedArg("yAxis") final Axis<Y> yAxis,
                                   @NamedArg("data") final ObservableList<Series<X, Y>> data) {

        super(xAxis, yAxis, data);

        listenToClearSelectionFromRemovedData();
        initializeLasso();
        handleSelectionEffect();
    }

    /**
     *
     */
    private void listenToClearSelectionFromRemovedData() {

        // for any data-item removed from a series, remove it from the selection
        final ListChangeListener<? super Data<X, Y>> dataListChangedListener = dataListChanged -> {
            while (dataListChanged.next()) {
                currentSelection.removeAll(dataListChanged.getRemoved());
            }
        };

        // remove data-items for removed series and keep on track with the above listener for
        // added/removed series
        getData().addListener((ListChangeListener<? super javafx.scene.chart.XYChart.Series<X, Y>>) seriesListChanged -> {
            while (seriesListChanged.next()) {
                seriesListChanged.getAddedSubList().forEach(seriesAdded -> seriesAdded.getData().addListener(dataListChangedListener));
                seriesListChanged.getRemoved().forEach(seriesRemoved -> {
                    currentSelection.removeAll(seriesRemoved.getData());
                    seriesRemoved.getData().removeListener(dataListChangedListener);
                });
            }
        });
    }

    /**
     *
     */
    private void initializeLasso() {
        // add lasso to chart and set visuals
        getPlotChildren().add(lasso);
        lasso.toFront();
        lasso.getStrokeDashArray().add(3.0);
        lasso.setFill(Color.gray(0.5, 0.05));
        lasso.setStroke(Color.GRAY);

        // update the lasso to follow the mouse-movement
        addEventHandler(MouseEvent.MOUSE_DRAGGED, event -> {
            final Point2D sceneCoordinates = new Point2D(event.getSceneX(), event.getSceneY());
            final double localX = getXAxis().sceneToLocal(sceneCoordinates).getX();
            final double localY = getYAxis().sceneToLocal(sceneCoordinates).getY();
            lasso.getPoints().addAll(localX, localY);
        });

        // update current selection and clear lasso
        addEventHandler(MouseEvent.MOUSE_RELEASED, event -> {
            final List<Data<X, Y>> selection = getData().stream().flatMap(series -> series.getData().parallelStream()).filter(dataItem -> {

                // get any data-item contained within the lasso-area
                final Point2D dataItemDisplayPosition = new Point2D(getXAxis().getDisplayPosition(dataItem.getXValue()),
                                                                    getYAxis().getDisplayPosition(dataItem.getYValue()));

                return lasso.contains(dataItemDisplayPosition);

            }).collect(Collectors.toList());

            // add to selection if shift is pressed, otherwise replace it
            if (event.isShiftDown()) {
                selection.removeAll(currentSelection);
                currentSelection.addAll(selection);
            } else {
                currentSelection.setAll(selection);
            }

            // clear the lasso
            lasso.getPoints().clear();
        });
    }

    /**
     *
     */
    private void handleSelectionEffect() {
        // apply selection effect to selected data-nodes
        currentSelection.addListener((ListChangeListener<? super Data<X, Y>>) dataChanged -> {
            while (dataChanged.next()) {
                dataChanged.getRemoved().forEach(removedDataItem -> {
                    removedDataItem.getNode().effectProperty().unbind();
                    removedDataItem.getNode().effectProperty().set(null);
                });
                dataChanged.getAddedSubList().forEach(addedDataItem -> {
                    addedDataItem.getNode().effectProperty().bind(selectionEffect); // TODO fails when node isn't displayed yet! (is that the reason?)
                });
            }
        });
    }

    /**
     * @return the lasso
     */
    public final Polyline getLasso() {
        return lasso;
    }

    /**
     * @return currentSelection (unmodifiable)
     */
    public final ObservableList<Data<X, Y>> getCurrentSelectionUnmodifiable() {
        return currentSelectionUnmodifiable;
    }


    public final ObjectProperty<Effect> selectionEffectProperty() {
        return this.selectionEffect;
    }

    public final Effect getSelectionEffect() {
        return this.selectionEffectProperty().get();
    }


    public final void setSelectionEffect(final Effect selectionEffect) {
        this.selectionEffectProperty().set(selectionEffect);
    }

    public void setCurrentSelection(List<Data<X, Y>> selection) {
        this.currentSelection.setAll(selection);
    }

}
