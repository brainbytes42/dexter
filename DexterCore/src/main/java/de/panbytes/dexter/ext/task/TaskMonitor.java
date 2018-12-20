package de.panbytes.dexter.ext.task;

import com.google.common.base.Preconditions;
import de.panbytes.dexter.lib.util.reactivex.extensions.RxField;
import de.panbytes.dexter.lib.util.reactivex.extensions.RxFieldReadOnly;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TaskMonitor {

    @Deprecated
    public static TaskMonitor evilReference;

    public TaskMonitor() {
        evilReference = this;
    }

    private final RxField<List<ObservableTask<?>>> currentTasks = RxField.<List<ObservableTask<?>>>withInitialValue(new ArrayList<>()).wrappingReturnedValues(
            Collections::unmodifiableList);

    public void addTask(ObservableTask<?> task) {
        synchronized (this.currentTasks) {
            ArrayList<ObservableTask<?>> added = new ArrayList<>(this.currentTasks.getValue());
            added.add(Preconditions.checkNotNull(task));
            this.currentTasks.setValue(added);
        }

        // wait for finished tasks to remove them
        task.getState()
            .toObservable()
            .filter(state -> state == ObservableTask.State.SUCCESSFUL || state == ObservableTask.State.FAILED || state == ObservableTask.State.CANCELED)
            .firstElement()
            .subscribe(__ -> {
                synchronized (this.currentTasks) {
                    ArrayList<ObservableTask<?>> removed = new ArrayList<>(this.currentTasks.getValue());
                    removed.remove(task);
                    this.currentTasks.setValue(removed);
                }
            });
    }


    public RxFieldReadOnly<List<ObservableTask<?>>> getCurrentTasks() {
        return this.currentTasks.toReadOnlyView();
    }


}
