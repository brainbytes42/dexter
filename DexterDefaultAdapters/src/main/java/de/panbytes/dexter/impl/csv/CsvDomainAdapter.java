package de.panbytes.dexter.impl.csv;

import de.panbytes.dexter.core.context.AppContext;
import de.panbytes.dexter.core.domain.DomainAdapter;

class CsvDomainAdapter extends DomainAdapter {

    CsvDomainAdapter(AppContext appContext) {

        super("CSV-DomainAdapter", "DomainAdapter for CSV-Files using Format ID;CLASS;Feature1;...;FeatureN", appContext);

        getDataSourceActions().setAddActions(new AddCsvFileAction(this, appContext));

    }

}
