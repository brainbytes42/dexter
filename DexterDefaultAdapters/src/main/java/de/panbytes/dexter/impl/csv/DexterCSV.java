package de.panbytes.dexter.impl.csv;

import de.panbytes.dexter.appfx.DexterApp;
import de.panbytes.dexter.core.context.AppContext;
import de.panbytes.dexter.core.domain.DomainAdapter;

public class DexterCSV extends DexterApp {

    @Override
    public String getDomainIdentifier() {
        return "DexterCSV";
    }

    @Override
    protected DomainAdapter createDomainAdapter(AppContext appContext) {
        return new CsvDomainAdapter(appContext);
    }

}
