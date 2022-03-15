package de.panbytes.dexter.appfx.settings;

import de.panbytes.dexter.core.context.GeneralSettings;
import io.reactivex.rxjavafx.observables.JavaFxObservable;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class DexterGeneralSettingsView {

    private final GeneralSettings settings;

    @FXML private Spinner<Double> perplexitySpinner;
    @FXML private Spinner<Integer> crossValidationRunsSpinner;
    @FXML private Spinner<Integer> crossValidationFoldsSpinner;
    @FXML private ComboBox<GeneralSettings.ConfusionMatrixInspectionOrder> confusionMatrixInspectionOrder;

    public DexterGeneralSettingsView(GeneralSettings settings) {
        this.settings = settings;
    }

    @FXML
    private void initialize() {
        perplexitySpinner.setValueFactory(
                new SpinnerValueFactory.DoubleSpinnerValueFactory(0., Double.MAX_VALUE, this.settings.getPerplexity().getValue()));
        JavaFxObservable.valuesOf(perplexitySpinner.valueProperty())
                        .debounce(500, TimeUnit.MILLISECONDS)
                        .subscribe(this.settings.getPerplexity()::setValue);

        crossValidationRunsSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, Integer.MAX_VALUE, this.settings.getCrossValidationRuns().getValue()));
        JavaFxObservable.valuesOf(crossValidationRunsSpinner.valueProperty())
                        .debounce(500, TimeUnit.MILLISECONDS)
                        .subscribe(this.settings.getCrossValidationRuns()::setValue);

        crossValidationFoldsSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(2, Integer.MAX_VALUE, this.settings.getCrossValidationFolds().getValue()));
        JavaFxObservable.valuesOf(crossValidationFoldsSpinner.valueProperty())
                        .debounce(500, TimeUnit.MILLISECONDS)
                        .subscribe(this.settings.getCrossValidationFolds()::setValue);

        confusionMatrixInspectionOrder.getItems().setAll(GeneralSettings.ConfusionMatrixInspectionOrder.values());
        confusionMatrixInspectionOrder.setValue(this.settings.getConfusionMatrixInspectionOrder().getValue());
        JavaFxObservable.valuesOf(confusionMatrixInspectionOrder.valueProperty())
                        .debounce(500, TimeUnit.MILLISECONDS)
                        .subscribe(this.settings.getConfusionMatrixInspectionOrder()::setValue);
    }

    public Node createView() {
        FXMLLoader fxmlLoader = new FXMLLoader(DexterGeneralSettingsView.class.getResource("DexterGeneralSettingsView.fxml"));
        fxmlLoader.setControllerFactory(__ -> this);

        Node view = null;
        try {
            view = fxmlLoader.load();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return view;
    }

}
