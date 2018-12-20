package de.panbytes.dexter.core;

import de.panbytes.dexter.core.data.DataEntity;
import de.panbytes.dexter.lib.util.reactivex.extensions.RxFieldCollection;
import de.panbytes.dexter.lib.util.reactivex.extensions.RxFieldReadOnly;
import io.reactivex.Observable;

import java.util.HashSet;
import java.util.Set;

public class InspectionHistory {

    private final RxFieldCollection<Set<DataEntity>, DataEntity> labeledEntities = RxFieldCollection.withInitialValue(
            new HashSet<>(), HashSet::new);

    InspectionHistory() {

        // keep out unlabeled entities
        this.labeledEntities.toObservable()
                            .switchMap(entities -> Observable.fromIterable(entities)
                                                             .flatMap(entity -> entity.getClassLabel()
                                                                                      .toObservable()
                                                                                      .filter(classLabel -> !classLabel.isPresent())
                                                                                      .map(__ -> entity)))
                            .subscribe(this.labeledEntities::remove);

    }

    public void markInspected(DataEntity entity) {
        this.labeledEntities.add(entity);
    }

    public RxFieldReadOnly<Set<DataEntity>> getLabeledEntities() {
        return this.labeledEntities.toReadOnlyView();
    }

    public void clear() {
        this.labeledEntities.clear();
    }

}
