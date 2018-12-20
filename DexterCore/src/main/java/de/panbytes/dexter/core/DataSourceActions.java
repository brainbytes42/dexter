package de.panbytes.dexter.core;

import com.google.common.base.Preconditions;
import de.panbytes.dexter.core.data.DataSource;
import de.panbytes.dexter.core.domain.DomainAdapter;
import de.panbytes.dexter.core.domain.FeatureSpace;
import de.panbytes.dexter.util.Named;
import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;


public class DataSourceActions {

    private static final Logger log = LoggerFactory.getLogger(DataSourceActions.class);
    private final Subject<List<AddAction>> addActions = BehaviorSubject.createDefault(Collections.emptyList());
    private final Subject<Optional<AddAction>> defaultAddAction = BehaviorSubject.createDefault(Optional.empty());

    public DataSourceActions() {
        initBindingForDefaultAddAction();
    }

    private void initBindingForDefaultAddAction() {
        getAddActions().subscribe(actions -> {
            if (actions.isEmpty()) {
                defaultAddAction.onNext(Optional.empty());
            } else if (!getDefaultAddAction().blockingFirst().isPresent() || !actions.contains(
                    getDefaultAddAction().blockingFirst().get())) {
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

    public Observable<Optional<AddAction>> getDefaultAddAction() {
        return defaultAddAction.distinctUntilChanged().hide();
    }

    public void setDefaultAddAction(AddAction defaultAddAction) {
        this.defaultAddAction.onNext(Optional.of(defaultAddAction));
    }


    public static abstract class AddAction extends Named.BaseImpl implements Named {

        private final DomainAdapter domainAdapter;

        protected AddAction(String name, String description, DomainAdapter domainAdapter) {
            super(name, description);
            this.domainAdapter = checkNotNull(domainAdapter);
        }

        public DomainAdapter getDomainAdapter() {
            return this.domainAdapter;
        }

        public Result createAndAdd(ActionContext context) {
            Collection<DataSource> dataSources = null;

            Result result;
            try {
                dataSources = createDataSources(context);

                try {
                    addToRoot(dataSources);
                    result = new Result(dataSources, Result.State.SUCCESS, "DataSource was successfully added.");

                } catch (Exception e) {
                    log.warn("Adding DataSource failed!", e);
                    result = new Result(dataSources, Result.State.ADDING_FAILED, e);
                }
            } catch (Exception e) {
                log.warn("Creating DataSource failed!", e);
                result = new Result(Collections.emptyList(), Result.State.CREATION_FAILED, e);
            }

            return result;
        }

        protected abstract Collection<DataSource> createDataSources(/*TODO: Context, ...?*/ActionContext context);

        void addToRoot(Collection<DataSource> dataSources) {
            addToRoot(dataSources, false);
        }

        void addToRoot(Collection<DataSource> dataSources, boolean forceFeatureSpace) {

            checkArgument(checkNotNull(dataSources).size() > 0, "Collection of DataSources to add is empty.");
            checkArgument(dataSources.stream().map(DataSource::getFeatureSpace).distinct().count() == 1,
                          "DataSources to be added have different FeatureSpaces!");

            FeatureSpace featureSpace = dataSources.iterator().next().getFeatureSpace();

            if (forceFeatureSpace) {
                domainAdapter.setFeatureSpace(featureSpace);
            }

            AtomicReference<RuntimeException> failure = new AtomicReference<>();

            domainAdapter.getRootDataSource()
                         .firstElement()
                         .map(dataSourceOptional -> dataSourceOptional.orElseThrow(
                                 () -> new IllegalStateException("Missing Root-DataSource in DomainAdapter!")))
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


        public static class Result {
            private final List<DataSource> createdSources = new ArrayList<>();
            private final State state;
            private final String message;
            private Throwable exception = null;

            private Result(Collection<DataSource> createdSources, State state, String message) {
                this.createdSources.addAll(createdSources);
                this.state = checkNotNull(state);
                this.message = checkNotNull(message);
            }

            private Result(Collection<DataSource> createdSources, State state, Throwable exception) {
                this.createdSources.addAll(createdSources);
                this.state = checkNotNull(state);
                this.message = checkNotNull(
                        exception.getLocalizedMessage() != null ? exception.getLocalizedMessage() : exception.toString());
                this.exception = exception;
            }

            public List<DataSource> getCreatedSources() {
                return Collections.unmodifiableList(createdSources);
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
                SUCCESS, CREATION_FAILED, ADDING_FAILED
            }
        }

        public static class ActionContext {
            private final Object source;

            public ActionContext(Object source) {this.source = source;}

            public Optional<Object> getSource() {
                return Optional.ofNullable(source);
            }
        }
    }
}
