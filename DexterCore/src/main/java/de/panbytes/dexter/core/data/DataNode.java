package de.panbytes.dexter.core.data;

import static com.google.common.base.Preconditions.checkNotNull;

import de.panbytes.dexter.core.domain.FeatureSpace;
import de.panbytes.dexter.lib.util.reactivex.extensions.RxField;
import de.panbytes.dexter.lib.util.reactivex.extensions.RxFieldReadOnly;
import de.panbytes.dexter.util.Named;
import io.reactivex.Observable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * The {@code DataNode} is the base class for the data-related class-hierarchies of {@link DataSource} and {@link DataEntity}.
 * It covers basic common properties like name, description and whether a node is enabled. Furthermore, it models the possibility
 * for a node to have children.
 */
public abstract class DataNode extends Named.BaseImpl implements Named{

    private final FeatureSpace featureSpace;

    private final RxField<List<DataNode>> childNodes = RxField.withInitialValue(Collections.emptyList());

    private final RxField<Status> status;
    private final Observable<Set<Status>> mergedBranchStatuses;

    @Deprecated
    private final RxField<EnabledState> enabledState = RxField.withInitialValue(EnabledState.ACTIVE);


    /**
     * Create a new Node with given name, description and feature-space.
     *
     * By default, the node is {@link Status#ACTIVE}.
     *
     * @param name        the node's name.
     * @param description the node's description.
     * @param featureSpace the node's feature space.
     * @throws NullPointerException if any parameter is null.
     */
    DataNode(String name, String description, FeatureSpace featureSpace) {
        this(name, description, featureSpace, Status.ACTIVE);
    }

    /**
     * Create a new Node with given name, description, feature-space and status.
     *
     * @param name        the node's name.
     * @param description the node's description.
     * @param featureSpace the node's feature space.
     * @param status the node's status.
     * @throws NullPointerException if any parameter is null.
     */
    DataNode(String name, String description, FeatureSpace featureSpace, Status status) {
        super(name, description);
        this.featureSpace = checkNotNull(featureSpace);
        this.status = RxField.withInitialValue(checkNotNull(status));

        this.mergedBranchStatuses = initMergedBranchStatuses(this.childNodes.toObservable(),
            this.status.toObservable());

        bindActiveStateWithChildren();
    }

    /**
     * Merge this node's status and the children's branch-statuses into a combined set and
     * return a hot observable providing the current branch-state to new subscribers.
     */
    private Observable<Set<Status>> initMergedBranchStatuses(Observable<List<DataNode>> childNodes,
        Observable<Status> status) {

        // merge the children's branch-statuses into a combined set.
        // - refresh for changed set of children (switchMap).
        // - ensure to get an empty set if no child is available (just..emptySet vs. combineLatest)!
        @SuppressWarnings("unchecked") // flatMap(child -> ((Set<Status>) child) is known to be safe.
        Observable<Set<Status>> mergedChildStatuses = childNodes.switchMap(
            children -> children.isEmpty() ? Observable.just(Collections.emptySet())
                : Observable.combineLatest(children.stream()
                                                   .map(DataNode::getMergedBranchStatuses)
                                                   .collect(Collectors.toList()),
                    childBranches -> Arrays.stream(childBranches)
                                           .flatMap(child -> ((Set<Status>) child).stream())
                                           .collect(Collectors.toSet())));

        // merge this node's status with the children's statuses.
        // the returned observable is hot and provides the current state to new subscribers.
        return Observable.combineLatest(status, mergedChildStatuses,
            (Status currentStatus, Set<Status> childStatuses) -> {
                Set<Status> mergedStatuses = new TreeSet<>(childStatuses);
                mergedStatuses.add(currentStatus);
                return mergedStatuses;
            }).distinctUntilChanged().replay(1).refCount();

    }

    /**
     * Returns the nodes status (observable).
     * @return the status-observable.
     */
    public Observable<Status> getStatus() {
        return this.status.toObservable();
    }

    /**
     * Set a new status if the current status is not protected ({@link Status#isProtected()}, e.g. ACTIVE or DISABLED).
     * @param newStatus the new status
     * @return true if the new status was set.
     * @see #setStatus(Status, boolean)
     */
    public boolean setStatus(Status newStatus){
        return setStatus(newStatus, false);
    }

    /**
     * Set a new status.
     * @param newStatus the new status
     * @param overwriteProtected if true, set status even if current status is protected ({@link Status#isProtected()}, e.g. REJECTED or INVALID).
     * @return true if the new status was set.
     */
    public boolean setStatus(Status newStatus, boolean overwriteProtected) {
        synchronized (this.status) {
            if (!this.status.getValue().isProtected() || overwriteProtected) {
                // only set the new status if the current status is not a protected status
                // or if the overwrite flag is set!
                this.status.setValue(newStatus);
                return true;
            } else {
                return false;
            }
        }
    }

    public Observable<Set<Status>> getMergedBranchStatuses() {
        return this.mergedBranchStatuses;
    }


    /**
     * Get the node's {@link EnabledState}.
     *
     * @return a (non-empty) {@link Observable} representing changes (w.r.t. {@link Object#equals(Object)}) to the node's active-state,
     * including the last value before subscribing.
     */
    @Deprecated
    public final RxFieldReadOnly<EnabledState> getEnabledState() {
        return this.enabledState.toReadOnlyView();
    }

    /**
     * Set this node active or disabled.
     *
     * @param active {@code true} for setting {@link EnabledState#ACTIVE}, {@code false} for setting {@link EnabledState#DISABLED}.
     */
    @Deprecated
    public final DataNode setEnabled(boolean active) {
        this.enabledState.setValue(active?EnabledState.ACTIVE:EnabledState.DISABLED);
        return this;
    }

    /**
     * Provide a List of all (immediate) child nodes.
     *
     * @return a (non-empty) observable for the (possibly empty) list of children.
     */
    public final RxFieldReadOnly<List<DataNode>> getChildNodes() {
        return this.childNodes.toReadOnlyView();
    }

    /**
     * Set the current child nodes.
     *
     * @param childNodes the collection of child nodes.
     * @throws NullPointerException if collection is null.
     */
    final DataNode setChildNodes(Collection<DataNode> childNodes) {
        this.childNodes.setValue(Collections.unmodifiableList(new ArrayList<>(checkNotNull(childNodes))));
        return this;
    }

    /**
     * Initialize the binding of the node's activeState to and from the node's children.
     */
    @Deprecated
    private void bindActiveStateWithChildren() {
        Observable<List<DataNode>> children = getChildNodes().toObservable();

        // push this node's state-changes to the children (enable/disable)
        getEnabledState().toObservable().subscribe(enabledState -> {
            switch (enabledState) {
                case ACTIVE:
                    children.blockingFirst().forEach(child -> child.setEnabled(true));
                    break;
                case DISABLED:
                    children.blockingFirst().forEach(child -> child.setEnabled(false));
                    break;
                case PARTIAL:
                    //no action
                    break;
                default:
                    throw new IllegalStateException("Unknown State: " + enabledState);
            }
        });

        // pull the children's changes to fit this node's state (e.g. PARTIAL if some child gets disabled).
        children.switchMap(currentChildNodes -> {
            // any one of the current children's activeState-Observable's may fire...
            return Observable.merge(currentChildNodes.stream().map(DataNode::getEnabledState).map(RxFieldReadOnly::toObservable).collect(Collectors.toList()));
        }).subscribe(childState -> {
            // ... and if any child changes it's state, this node's state gets adapted.
            Collection<DataNode> currentChildren = children.blockingFirst();
            List<EnabledState> childStatesDistinct = currentChildren.stream()
                                                                    .map(child -> child.getEnabledState().getValue())
                                                                    .distinct()
                                                                    .collect(Collectors.toList());
            if (childStatesDistinct.size() == 1) {
                this.enabledState.setValue(childStatesDistinct.get(0));
            } else if (childStatesDistinct.size() > 1) {
                this.enabledState.setValue(EnabledState.PARTIAL);
            } else {
                throw new IllegalStateException(
                        "distinct states of children not expected to be of length less than 1, but is: " + childStatesDistinct);
            }
        });
    }

    public FeatureSpace getFeatureSpace() {
        return this.featureSpace;
    }

    /**
     * The node's state describing whether it is considered {@code ACTIVE} or {@code DISABLED}.
     * The third state {@code PARTIAL} is relevant for nodes containing children, where the children aren't in a uniform state.
     */
    @Deprecated
    public enum EnabledState {
        ACTIVE, DISABLED, PARTIAL
    }

    public enum Status {
        ACTIVE(false), DISABLED(false), REJECTED(true), INVALID(true);

        private final boolean isProtected;

        Status(boolean isProtected) {
            this.isProtected = isProtected;
        }

        public boolean isProtected() {
            return isProtected;
        }
    }
}
