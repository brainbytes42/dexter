package de.panbytes.dexter.v2.demo;

import de.panbytes.dexter.appfx.DexterApp;
import de.panbytes.dexter.core.context.AppContext;
import de.panbytes.dexter.core.data.ClassLabel;
import de.panbytes.dexter.core.data.DataNode;
import de.panbytes.dexter.core.data.DataSource;
import de.panbytes.dexter.core.domain.DataSourceActions;
import de.panbytes.dexter.core.domain.DomainAdapter;
import de.panbytes.dexter.core.data.DomainDataEntity;
import de.panbytes.dexter.core.domain.FeatureSpace;
import mnist.MnistReader;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class DexterDemo extends DexterApp {

    private final FeatureSpace featureSpace;

    public DexterDemo() {
        List<FeatureSpace.Feature> features = IntStream.range(0, 784)
                                                       .mapToObj(i -> new FeatureSpace.Feature("x_" + i))
                                                       .collect(Collectors.toList());
        featureSpace = new FeatureSpace("MNIST", features);
    }

    public static void main(String[] args) throws URISyntaxException {

        launch(args);

    }

    @Override
    protected String getDomainIdentifier() {
        return "Demo";
    }

    @Override
    protected DomainAdapter createDomainAdapter(AppContext appContext) {
        return new DemoDomain("Demo", "Domain Adapter for Demonstrator.", appContext);
    }

    private class MnistDataSource extends DataSource {

        private final int maxEntities;

        /**
         * Create a new {@code DataSource} with the given name.
         *
         * @param name         the DataSource's name.
         * @param description
         * @param featureSpace
         * @param maxEntities
         * @throws NullPointerException if the name is null.
         * @see DataNode#DataNode(String, String, FeatureSpace)
         */
        protected MnistDataSource(String name, String description, FeatureSpace featureSpace, int maxEntities) {
            super(name, description, featureSpace);
            this.maxEntities = maxEntities;

            this.setGeneratedDataEntities(readData());
        }

        public List<DomainDataEntity> readData() {

            URL imagesResource = DexterDemo.class.getResource("/datasets/mnist/train-images.idx3-ubyte");
            URL labelsResource = DexterDemo.class.getResource("/datasets/mnist/train-labels.idx1-ubyte");

            Path imagesPath = null;
            Path labelsPath = null;
            try {
                imagesPath = Paths.get(imagesResource.toURI());
                labelsPath = Paths.get(labelsResource.toURI());
            } catch (URISyntaxException e) {
                e.printStackTrace();
                return Collections.emptyList();
            }

            List<int[][]> images = MnistReader.getImages(imagesPath.toString());
            int[] labels = MnistReader.getLabels(labelsPath.toString());

            if (images.size() != labels.length) {
                throw new IllegalStateException(
                        "Number of Images (" + images.size() + ") has to match number of Labels (" + labels.length + ")!");
            }


            List<DomainDataEntity> result = new ArrayList<>();
            for (int i = 0; i < images.size(); i++) {
                DomainDataEntity dataEntity = new DomainDataEntity("Img#" + i, "Image No. " + i + " with label " + labels[i],
                                                                   Arrays.stream(images.get(i))
                                                                         .flatMapToInt(Arrays::stream)
                                                                         .mapToDouble(intValue -> (double) intValue)
                                                                         .toArray(), featureSpace, this);
                dataEntity.setClassLabel(ClassLabel.labelFor(String.valueOf(labels[i])));

                result.add(dataEntity);
            }
            Collections.shuffle(result);

            int targetNumberOfEntities = Math.min(images.size(), this.maxEntities);

            return result.subList(0, targetNumberOfEntities);
        }
    }

    private class DemoDomain extends DomainAdapter {

        public DemoDomain(String name, String description, AppContext appContext) {
            super(name, description,appContext);

            this.getDataSourceActions().setAddActions(new DataSourceActions.AddAction("MNIST", "MNIST Dataset", this) {
                @Override
                protected Optional<Collection<DataSource>> createDataSources(ActionContext context) {
                    return Arrays.asList(new MnistDataSource("MNIST", "MNIST Dataset", featureSpace, 5000));
                }
            });
        }

    }
}
