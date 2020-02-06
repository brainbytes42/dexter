package de.panbytes.dexter.appfx.scene.control;

import javafx.beans.NamedArg;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.DoubleProperty;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.Background;
import javafx.scene.layout.StackPane;

public class ProgressBarWithLabel extends StackPane {

    private final ProgressBar progressBar = new ProgressBar();
    private final TextField textField = new TextField();

    public ProgressBarWithLabel() {
        this(ProgressIndicator.INDETERMINATE_PROGRESS);
    }

    public ProgressBarWithLabel(@NamedArg("progress") double progress) {

        progressProperty().set(progress);

        initProgressBar();
        initTextField();

        getChildren().setAll(progressBar, textField);

    }

    private void initProgressBar() {
        progressBar.styleProperty().bind(new StringBinding() {
            {
                super.bind(progressProperty());
            }

            @Override
            protected String computeValue() {
                final double p = progressProperty().get();
                final String color = p >= 0 ? "hsb(" + (-(Math.sqrt(p) * 120) + 120) + ", 80%, " + ((Math.sqrt(p) + 2) / 3.0) * 100 + "%)" : "DarkGray";
                return "-fx-accent: " + color + ";";
            }
        });
        progressBar.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    }

    private void initTextField() {
        textField.textProperty().bind(new StringBinding() {
            {
                super.bind(progressProperty());
            }

            @Override
            protected String computeValue() {
                final double p = progressProperty().get();
                return p >= 0 ? String.format("%.1f %%", p * 100) : "...";
            }
        });
        textField.setEditable(false);
        textField.setFocusTraversable(false);
        textField.setBackground(Background.EMPTY);
    }

    public DoubleProperty progressProperty() {
        return progressBar.progressProperty();
    }
}