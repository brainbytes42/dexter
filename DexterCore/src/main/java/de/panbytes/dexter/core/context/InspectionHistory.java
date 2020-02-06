package de.panbytes.dexter.core.context;

import de.panbytes.dexter.core.data.DataEntity;
import de.panbytes.dexter.lib.util.reactivex.extensions.RxFieldCollection;
import de.panbytes.dexter.lib.util.reactivex.extensions.RxFieldReadOnly;
import de.panbytes.dexter.util.RxJavaUtils;
import io.reactivex.Observable;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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

    public Observable<Set<DataEntity>> getLabeledEntities() {
        return this.labeledEntities.toObservable().compose(RxJavaUtils.deepFilter(DataEntity::classLabelObs, Optional::isPresent, Collectors.toSet()));
    }

    public void clear() {
        this.labeledEntities.clear();
    }

}
