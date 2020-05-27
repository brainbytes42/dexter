package de.panbytes.dexter.core.domain;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Preconditions;
import de.panbytes.dexter.core.data.DataSource;
import de.panbytes.dexter.core.domain.DataSourceActions.AddAction.Result.State;
import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class DataSourceActions {

    private static final Logger log = LoggerFactory.getLogger(DataSourceActions.class);
    private final BehaviorSubject<List<AddAction>> addActions = BehaviorSubject.createDefault(Collections.emptyList());
    private final BehaviorSubject<Optional<AddAction>> defaultAddAction = BehaviorSubject.createDefault(Optional.empty());

    public DataSourceActions() {
        initBindingForDefaultAddAction();
    }

    private void initBindingForDefaultAddAction() {
        getAddActions().subscribe(actions -> {
            if (actions.isEmpty()) {
                defaultAddAction.onNext(Optional.empty());
            } else if (!getDefaultAddAction().blockingFirst().isPresent() || !actions.contains(getDefaultAddAction().blockingFirst().get())) {
                defaultAddAction.onNext(Optional.of(actions.get(0)));
            }
        });

        getDefaultAddAction().subscribe(action -> {
            List<AddAction> addActions = getAddActions().blockingFirst();
            if (action.isPresent() && !addActions.contains(action.get())) {
                addActions = new ArrayList<>(addActions);
                addActions.add(0, action.get());
                setAddActions(addActions);
            }
        });
    }

    public Observable<List<AddAction>> getAddActions() {
        return addActions.distinctUntilChanged().hide();
    }

    public void setAddActions(AddAction... addActions) {
        setAddActions(Arrays.asList(addActions));
    }

    public void setAddActions(Collection<AddAction> addActions) {
        checkNotNull(addActions).forEach(Preconditions::checkNotNull);
        this.addActions.onNext(Collections.unmodifiableList(new ArrayList<>(addActions)));
    }

    public void addAddAction(AddAction newAction) {
        checkNotNull(newAction);
        final ArrayList<AddAction> actions = new ArrayList<>(this.addActions.getValue());
        actions.add(newAction);
        setAddActions(actions);
    }

    public boolean removeAddAction(AddAction removeAction) {
        checkNotNull(removeAction);
        final ArrayList<AddAction> actions = new ArrayList<>(this.addActions.getValue());
        final boolean isRemoved = actions.remove(removeAction);
        if (isRemoved) {
            setAddActions(actions);
        }
        return isRemoved;
    }

    public Observable<Optional<AddAction>> getDefaultAddAction() {
        return defaultAddAction.distinctUntilChanged().hide();
    }

    public void setDefaultAddAction(AddAction defaultAddAction) {
        this.defaultAddAction.onNext(Optional.of(defaultAddAction));
    }


    public static abstract class AddAction extends de.panbytes.dexter.util.Named.BaseImpl implements de.panbytes.dexter.util.Named {

        private final DomainAdapter domainAdapter;

        protected AddAction(String name, String description, DomainAdapter domainAdapter) {
            super(name, description);
            this.domainAdapter = checkNotNull(domainAdapter);
        }

        public final DomainAdapter getDomainAdapter() {
            return this.domainAdapter;
        }

        public final Result createAndAdd(ActionContext context) {
            Result result;
            try {
                // create DataSources
                Optional<Collection<DataSource>> dataSources = createDataSources(context);

                // add DataSources
                result = dataSources.map(ds -> {
                    try {
                        addToRoot(ds);
                        return new Result(ds, Result.State.SUCCESS, "DataSource was successfully added.");

                    } catch (Exception e) {
                        log.warn("Adding DataSource failed!", e);
                        return new Result(ds, Result.State.ADDING_FAILED, e);
                    }
                }).orElseGet(() -> new Result(null, State.CANCELLED, "Creation of DataSource was cancelled."));

            } catch (Exception e) {
                log.warn("Creating DataSource failed!", e);
                result = new Result(Collections.emptyList(), Result.State.CREATION_FAILED, e);
            }

            // log depending on result
            if (result.state == State.SUCCESS || result.state == State.CANCELLED) {
                log.debug("DataSourceAction: {} - {}", result.state, result.message);
            } else if (result.state == State.ADDING_FAILED || result.state == State.CREATION_FAILED) {
                log.warn("DataSourceAction: {} - {}", result.state, result.message, result.exception);
            }

            return result;
        }

        protected abstract Optional<Collection<DataSource>> createDataSources(ActionContext context);

        final void addToRoot(Collection<DataSource> dataSources) {
            addToRoot(dataSources, false);
        }

        final void addToRoot(Collection<DataSource> dataSources, boolean forceFeatureSpace) {

            checkNotNull(dataSources);
            if (dataSources.isEmpty()) {
                log.warn("Collection of DataSources to add is empty.");
                return;
            }

            checkArgument(dataSources.stream().map(DataSource::getFeatureSpace).distinct().count() == 1,
                          "DataSources to be added have different FeatureSpaces!");

            FeatureSpace featureSpace = dataSources.iterator().next().getFeatureSpace();

            if (forceFeatureSpace) {
                domainAdapter.setFeatureSpace(featureSpace);
            }

            AtomicReference<RuntimeException> failure = new AtomicReference<>();

            domainAdapter
                .getRootDataSource()
                .firstElement()
                .map(dataSourceOptional -> dataSourceOptional.orElseThrow(() -> new IllegalStateException("Missing Root-DataSource in DomainAdapter!")))
                .doOnError(__ -> domainAdapter.setFeatureSpace(featureSpace))
                .retry(1)
                .subscribe(root -> {
                    try {
                        root.addChildDataSources(dataSources);
                    } catch (RuntimeException ex) {
                        failure.set(ex);
                    }
                });

            if (failure.get() != null) {
                throw failure.get();
            }

        }


        public static final class Result {

            private final List<DataSource> createdSources;
            private final State state;
            private final String message;
            private Throwable exception = null;

            private Result(Collection<DataSource> createdSources, State state, String message) {
                this.createdSources = createdSources == null ? null : new ArrayList<>(createdSources);
                this.state = checkNotNull(state);
                this.message = checkNotNull(message);
            }

            private Result(Collection<DataSource> createdSources, State state, Throwable exception) {
                this.createdSources = createdSources == null ? null : new ArrayList<>(createdSources);
                this.state = checkNotNull(state);
                this.message = checkNotNull(exception.getLocalizedMessage() != null ? exception.getLocalizedMessage() : exception.toString());
                this.exception = exception;
            }

            public Optional<List<DataSource>> getCreatedSources() {
                return Optional.ofNullable(createdSources).map(Collections::unmodifiableList);
            }

            public State getState() {
                return state;
            }

            public String getMessage() {
                return message;
            }

            public Optional<Throwable> getException() {
                return Optional.ofNullable(this.exception);
            }

            public enum State {
                SUCCESS, CREATION_FAILED, ADDING_FAILED, CANCELLED
            }
        }

        public static final class ActionContext {

            private final Object source;

            public ActionContext(Object source) {
                this.source = source;
            }

            public Optional<Object> getSource() {
                return Optional.ofNullable(source);
            }
        }
    }
}
