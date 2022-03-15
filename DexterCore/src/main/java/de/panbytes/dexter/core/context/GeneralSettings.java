package de.panbytes.dexter.core.context;

import de.panbytes.dexter.ext.prefs.RxPreference;
import de.panbytes.dexter.ext.prefs.RxPreferenceBoolean;
import de.panbytes.dexter.ext.prefs.RxPreferenceDouble;
import de.panbytes.dexter.ext.prefs.RxPreferenceInt;
import javafx.scene.Node;

import java.util.Optional;
import java.util.function.Supplier;

public class GeneralSettings implements SettingsStorage {

    private final RxPreferenceDouble perplexity;
    private final RxPreferenceBoolean classificationOnFilteredData;
    private final RxPreferenceInt crossValidationRuns;
    private final RxPreferenceInt crossValidationFolds;
    private final RxPreference<ConfusionMatrixInspectionOrder> confusionMatrixInspectionOrder;

    private Supplier<Node> viewSupplier;

    public GeneralSettings(String domainIdentifier) {

        this.perplexity = RxPreference.createForIdentifier(GeneralSettings.class, domainIdentifier, "tsnePerplexity").buildWithDefaultValue(30.);
        this.classificationOnFilteredData = RxPreference.createForIdentifier(GeneralSettings.class, domainIdentifier, "classificationOnFilteredData")
                                                                               .buildWithDefaultValue(true);
        this.crossValidationRuns = RxPreference.createForIdentifier(GeneralSettings.class,domainIdentifier,"crossValidationRuns").buildWithDefaultValue(3);
        this.crossValidationFolds = RxPreference.createForIdentifier(GeneralSettings.class,domainIdentifier,"crossValidationFolds").buildWithDefaultValue(10);
        this.confusionMatrixInspectionOrder = RxPreference.<ConfusionMatrixInspectionOrder>createForIdentifier(GeneralSettings.class, domainIdentifier, "confusionMatrixInspectionOrder")
                                                          .withDefaultValue(ConfusionMatrixInspectionOrder.MOST_UNCERTAIN_FIRST)
                                                          .withMarshalling(Enum::name, ConfusionMatrixInspectionOrder::valueOf)
                                                          .build();
    }

    @Override
    public Optional<Node> getSettingsView() {
        return Optional.ofNullable(viewSupplier.get());
    }


    public RxPreferenceDouble getPerplexity() {
        return this.perplexity;
    }

    public RxPreferenceBoolean getClassificationOnFilteredData() {
        return this.classificationOnFilteredData;
    }

    public void setSettingsViewSupplier(Supplier<Node> viewSupplier) {
        this.viewSupplier = viewSupplier;
    }

    public RxPreferenceInt getCrossValidationRuns() {
        return crossValidationRuns;
    }

    public RxPreferenceInt getCrossValidationFolds() {
        return crossValidationFolds;
    }

    public RxPreference<ConfusionMatrixInspectionOrder> getConfusionMatrixInspectionOrder() {
        return confusionMatrixInspectionOrder;
    }

    public enum ConfusionMatrixInspectionOrder{
        MOST_UNCERTAIN_FIRST, LEAST_UNCERTAIN_FIRST
    }

}
