package de.panbytes.dexter.core.domain;

import de.panbytes.dexter.core.data.DataSource;
import de.panbytes.dexter.core.domain.DataSourceActions.AddAction;
import de.panbytes.dexter.core.model.DexterModel;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

@Deprecated // useful?
public abstract class ActiveLearningAddAction extends AddAction {

    private final DexterModel dexterModel;

    protected ActiveLearningAddAction(String name, String description, DomainAdapter domainAdapter, DexterModel dexterModel) {
        super(name, description, domainAdapter);
        this.dexterModel = dexterModel;
    }

    @Override
    protected final Optional<Collection<DataSource>> createDataSources(ActionContext context) {

        try {
            return loadData(context).map(Collections::singleton);

//            dexterModel.getClassificationModel()

//            return Collections.singleton(dataSource);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    abstract protected Optional<DataSource> loadData(ActionContext context) throws IOException;
}
