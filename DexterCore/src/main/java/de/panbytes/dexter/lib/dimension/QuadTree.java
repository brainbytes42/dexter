package de.panbytes.dexter.lib.dimension;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.*;
import java.util.stream.Collectors;

public class QuadTree {

    private final Collection<Entity> subtreeEntities;
    private final Rectangle2D boundingBox;

    private final Point2D centerOfMass;

    private Collection<QuadTree> subtrees = new ArrayList<>(4);

    public QuadTree create(Collection<Entity> entities) {
        Double maxX = entities.stream().map(Entity::getPosition).mapToDouble(Point2D::getX).max().getAsDouble();
        Double minX = entities.stream().map(Entity::getPosition).mapToDouble(Point2D::getX).min().getAsDouble();
        Double maxY = entities.stream().map(Entity::getPosition).mapToDouble(Point2D::getY).max().getAsDouble();
        Double minY = entities.stream().map(Entity::getPosition).mapToDouble(Point2D::getY).min().getAsDouble();

        return new QuadTree(entities, new Rectangle2D.Double(minX, minY, maxX - minX, maxY - minY), 1);
    }

    private QuadTree(Collection<Entity> entities, Rectangle2D boundingBox, int maxEntitiesBeforeSplit) {
        this.subtreeEntities = entities;
        this.boundingBox = boundingBox;

        if (entities.size() > maxEntitiesBeforeSplit) {

            Collection<Rectangle2D> subtreeBoundingBoxes = new ArrayList<>(4);
            subtreeBoundingBoxes.add(new Rectangle2D.Double(boundingBox.getX(), boundingBox.getY(), boundingBox.getWidth() / 2.0,
                                                            boundingBox.getHeight() / 2.0));
            subtreeBoundingBoxes.add(new Rectangle2D.Double(boundingBox.getCenterX(), boundingBox.getY(), boundingBox.getWidth() / 2.0,
                                                            boundingBox.getHeight() / 2.0));
            subtreeBoundingBoxes.add(new Rectangle2D.Double(boundingBox.getX(), boundingBox.getCenterY(), boundingBox.getWidth() / 2.0,
                                                            boundingBox.getHeight() / 2.0));
            subtreeBoundingBoxes.add(new Rectangle2D.Double(boundingBox.getCenterX(), boundingBox.getCenterY(), boundingBox.getWidth() / 2.0,
                                           boundingBox.getHeight() / 2.0));

            subtreeBoundingBoxes.forEach(subtreeBoundingBox->{
                List<Entity> subtreeEntities = entities.stream()
                                                         .filter(entity -> subtreeBoundingBox.contains(entity.getPosition()))
                                                         .collect(Collectors.toList());
                if(!subtreeEntities.isEmpty()){
                    this.subtrees.add(new QuadTree(subtreeEntities, subtreeBoundingBox, maxEntitiesBeforeSplit));
                }
            });

            double x = this.subtrees.stream().map(QuadTree::getCenterOfMass).mapToDouble(Point2D::getX).sum() / (double) this.subtrees.size();
            double y = this.subtrees.stream().map(QuadTree::getCenterOfMass).mapToDouble(Point2D::getX).sum() / (double) this.subtrees.size();
            this.centerOfMass = new Point2D.Double(x, y);
        } else {
            double x = entities.stream().map(Entity::getPosition).mapToDouble(Point2D::getX).sum() / (double) entities.size();
            double y = entities.stream().map(Entity::getPosition).mapToDouble(Point2D::getY).sum() / (double) entities.size();
            this.centerOfMass = new Point2D.Double(x, y);
        }
    }

    public Point2D getCenterOfMass() {
        return (Point2D) this.centerOfMass.clone();
    }

    interface Entity {
        Point2D getPosition();
    }

}
