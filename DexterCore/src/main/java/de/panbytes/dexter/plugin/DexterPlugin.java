package de.panbytes.dexter.plugin;

import de.panbytes.dexter.util.Named;
import de.panbytes.dexter.core.context.SettingsStorage;
import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.Subject;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public abstract class DexterPlugin extends Named.BaseImpl implements Named {

    private Subject<List<Action>> actions = BehaviorSubject.createDefault(Collections.emptyList());

    protected DexterPlugin(String name, String description) {
        super(name, description);
    }

    public final Observable<List<Action>> getActions() {
        return this.actions.distinctUntilChanged();
    }

    protected final void setActions(List<Action> actions) {
        this.actions.onNext(Collections.unmodifiableList(actions));
    }

    public Optional<SettingsStorage> getPluginSettings() {
        return Optional.empty();
    }


    public abstract class Action extends Named.BaseImpl implements Named {

        protected Action(String name, String description) {
            super(name, description);
        }

        public abstract void performAction(Object source);

    }

}
