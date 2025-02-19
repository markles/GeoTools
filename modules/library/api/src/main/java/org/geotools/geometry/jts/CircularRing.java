/*
 *    GeoTools - The Open Source Java GIS Toolkit
 *    http://geotools.org
 * 
 *    (C) 2014, Open Source Geospatial Foundation (OSGeo)
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation;
 *    version 2.1 of the License.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 */
package org.geotools.geometry.jts;

import java.util.Arrays;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.CoordinateFilter;
import com.vividsolutions.jts.geom.CoordinateSequence;
import com.vividsolutions.jts.geom.CoordinateSequenceComparator;
import com.vividsolutions.jts.geom.CoordinateSequenceFilter;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryComponentFilter;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.GeometryFilter;
import com.vividsolutions.jts.geom.IntersectionMatrix;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.PrecisionModel;
import com.vividsolutions.jts.geom.impl.CoordinateArraySequence;

/**
 * A CircularRing is a CircularString whose start and end point coincide. The ring needs to be
 * formed of at least two arc circles, in order to be able to determine its orientation.
 * 
 * @author Andrea Aime - GeoSolutions
 */
public class CircularRing extends LinearRing implements SingleCurvedGeometry<LinearRing>,
        CurvedRing {

    private static final long serialVersionUID = -5796254063449438787L;

    /**
     * This sequence is used as a fake to trick the constructor
     */
    static final CoordinateSequence FAKE_RING_2D = new CoordinateArraySequence(
            new Coordinate[] { new Coordinate(0, 0), new Coordinate(0, 1), new Coordinate(1, 1),
                    new Coordinate(0, 0) });

    CircularString delegate;

    public CircularRing(CoordinateSequence points, GeometryFactory factory, double tolerance) {
        super(FAKE_RING_2D, factory);
        delegate = new CircularString(points, factory, tolerance);
        if (!delegate.isClosed()) {
            throw new IllegalArgumentException(
                    "Start and end point are not matching, this is not a ring");
        }
    }

    public CircularRing(double[] controlPoints, GeometryFactory factory, double tolerance) {
        super(FAKE_RING_2D, factory);
        delegate = new CircularString(controlPoints, factory, tolerance);
        if (!delegate.isClosed()) {
            throw new IllegalArgumentException(
                    "Start and end point are not matching, this is not a ring");
        }
    }

    @Override
    public int getNumArcs() {
        return delegate.getNumArcs();
    }

    @Override
    public CircularArc getArcN(int arcIndex) {
        return delegate.getArcN(arcIndex);
    }

    @Override
    public LinearRing linearize() {
        CoordinateSequence cs = delegate.getLinearizedCoordinateSequence(delegate.tolerance);
        return getFactory().createLinearRing(cs);
    }

    public LinearRing linearize(double tolerance) {
        CoordinateSequence cs = delegate.getLinearizedCoordinateSequence(delegate.tolerance);
        return getFactory().createLinearRing(cs);
    }

    @Override
    public double getTolerance() {
        return delegate.getTolerance();
    }

    @Override
    public CoordinateSequence getLinearizedCoordinateSequence(double tolerance) {
        return delegate.getLinearizedCoordinateSequence(tolerance);
    }

    @Override
    public double[] getControlPoints() {
        return delegate.controlPoints;
    }

    /* Optimized overridden methods */

    public boolean isClosed() {
        return true;
    }

    public int getDimension() {
        return super.getDimension();
    }

    public int getBoundaryDimension() {
        return super.getDimension();
    }

    public boolean isEmpty() {
        return false;
    }

    public String getGeometryType() {
        return "CircularString";
    }

    @Override
    public int getCoordinatesDimension() {
        return delegate.getDimension();
    }

    public Geometry reverse() {
        double[] controlPoints = delegate.controlPoints;
        GrowableOrdinateArray array = new GrowableOrdinateArray();
        array.addAll(controlPoints);
        array.reverseOrdinates(0, array.size() - 1);
        return new CircularRing(array.getData(), getFactory(), delegate.tolerance);
    }

    public int getNumGeometries() {
        return 1;
    }

    public Geometry getGeometryN(int n) {
        return this;
    }

    public void setUserData(Object userData) {
        super.setUserData(userData);
    }

    public int getSRID() {
        return super.getSRID();
    }

    public void setSRID(int SRID) {
        super.setSRID(SRID);
    }

    public GeometryFactory getFactory() {
        return super.getFactory();
    }

    public Object getUserData() {
        return super.getUserData();
    }

    public PrecisionModel getPrecisionModel() {
        return super.getPrecisionModel();
    }

    public boolean isRectangle() {
        return false;
    }

    public Point getInteriorPoint() {
        return delegate.getInteriorPoint();
    }

    public Geometry getEnvelope() {
        return delegate.getEnvelope();
    }

    public Envelope getEnvelopeInternal() {
        return delegate.getEnvelopeInternal();
    }

    @Override
    protected Envelope computeEnvelopeInternal() {
        return delegate.getEnvelopeInternal();
    }

    public boolean equalsExact(Geometry other) {
        return equalsExact(other, 0);
    }

    public boolean equalsExact(Geometry other, double tolerance) {
        if (other instanceof CircularRing) {
            CircularRing csOther = (CircularRing) other;
            if (Arrays.equals(delegate.controlPoints, csOther.delegate.controlPoints)) {
                return true;
            }
        }
        return linearize(tolerance).equalsExact(other, tolerance);
    }

    public boolean equals(Geometry other) {
        if (other instanceof CircularRing) {
            CircularRing csOther = (CircularRing) other;
            if (Arrays.equals(delegate.controlPoints, csOther.delegate.controlPoints)) {
                return true;
            }
        }
        return linearize().equals(other);
    }

    public boolean equalsTopo(Geometry other) {
        if (other instanceof CircularRing) {
            CircularRing csOther = (CircularRing) other;
            if (Arrays.equals(delegate.controlPoints, csOther.delegate.controlPoints)) {
                return true;
            }
        }
        return linearize().equalsTopo(other);
    }

    public boolean equals(Object o) {
        if (o instanceof Geometry) {
            return equals((Geometry) o);
        } else {
            return false;
        }
    }

    public int hashCode() {
        return super.hashCode();
    }

    public String toString() {
        return toCurvedText();
    }

    public String toCurvedText() {
        StringBuilder sb = new StringBuilder("CIRCULARSTRING(");
        double[] controlPoints = delegate.controlPoints;
        for (int i = 0; i < controlPoints.length;) {
            sb.append(controlPoints[i++] + " " + controlPoints[i++]);
            if (i < controlPoints.length) {
                sb.append(", ");
            }
        }
        sb.append(")");
        return sb.toString();
    }

    public boolean equalsNorm(Geometry g) {
        return super.equalsNorm(g);
    }

    /*
     * Simple linearized delegate methods
     */

    public Coordinate[] getCoordinates() {
        return linearize().getCoordinates();
    }

    public CoordinateSequence getCoordinateSequence() {
        // trick to avoid issues while JTS validates the ring is closed,
        // it's calling super.isClosed() breaking the local override
        if (delegate != null) {
            return linearize().getCoordinateSequence();
        } else {
            return super.getCoordinateSequence();
        }
    }

    public Coordinate getCoordinateN(int n) {
        // trick to avoid issues while JTS validates the ring is closed,
        // it's calling super.isClosed() breaking the local override
        if (delegate != null) {
            return linearize().getCoordinateN(n);
        } else {
            return super.getCoordinateN(n);
        }
    }

    public Coordinate getCoordinate() {
        return linearize().getCoordinate();
    }

    public int getNumPoints() {
        // trick to avoid issues while JTS validates the ring is closed,
        // it's calling super.isClosed() breaking the local override
        if (delegate != null) {
            return linearize().getNumPoints();
        } else {
            return super.getNumPoints();
        }
    }

    public Point getPointN(int n) {
        return linearize().getPointN(n);
    }

    public Point getStartPoint() {
        return linearize().getStartPoint();
    }

    public Point getEndPoint() {
        return linearize().getEndPoint();
    }

    public boolean isRing() {
        return linearize().isRing();
    }

    public double getLength() {
        // todo: maybe compute the actual circular length?
        return linearize().getLength();
    }

    public Geometry getBoundary() {
        return linearize().getBoundary();
    }

    public boolean isCoordinate(Coordinate pt) {
        return linearize().isCoordinate(pt);
    }

    public void apply(CoordinateFilter filter) {
        linearize().apply(filter);
    }

    public void apply(CoordinateSequenceFilter filter) {
        linearize().apply(filter);
    }

    public void apply(GeometryFilter filter) {
        linearize().apply(filter);
    }

    public void apply(GeometryComponentFilter filter) {
        linearize().apply(filter);
    }

    public void normalize() {
        linearize().normalize();
    }

    public boolean isSimple() {
        return linearize().isSimple();
    }

    public boolean isValid() {
        return linearize().isValid();
    }

    public double distance(Geometry g) {
        return linearize().distance(g);
    }

    public boolean isWithinDistance(Geometry geom, double distance) {
        return linearize().isWithinDistance(geom, distance);
    }


    public double getArea() {
        return linearize().getArea();
    }

    public Point getCentroid() {
        return linearize().getCentroid();
    }

    public void geometryChanged() {
        linearize().geometryChanged();
    }

    public boolean disjoint(Geometry g) {
        return linearize().disjoint(g);
    }

    public boolean touches(Geometry g) {
        return linearize().touches(g);
    }

    public boolean intersects(Geometry g) {
        return linearize().intersects(g);
    }

    public boolean crosses(Geometry g) {
        return linearize().crosses(g);
    }

    public boolean within(Geometry g) {
        return linearize().within(g);
    }

    public boolean contains(Geometry g) {
        return linearize().contains(g);
    }

    public boolean overlaps(Geometry g) {
        return linearize().overlaps(g);
    }

    public boolean covers(Geometry g) {
        return linearize().covers(g);
    }

    public boolean coveredBy(Geometry g) {
        return linearize().coveredBy(g);
    }

    public boolean relate(Geometry g, String intersectionPattern) {
        return linearize().relate(g, intersectionPattern);
    }

    public IntersectionMatrix relate(Geometry g) {
        return linearize().relate(g);
    }



    public Geometry buffer(double distance) {
        return linearize().buffer(distance);
    }

    public Geometry buffer(double distance, int quadrantSegments) {
        return linearize().buffer(distance, quadrantSegments);
    }

    public Geometry buffer(double distance, int quadrantSegments, int endCapStyle) {
        return linearize().buffer(distance, quadrantSegments, endCapStyle);
    }

    public Geometry convexHull() {
        return linearize().convexHull();
    }

    public Geometry intersection(Geometry other) {
        return linearize().intersection(other);
    }

    public Geometry union(Geometry other) {
        return linearize().union(other);
    }

    public Geometry difference(Geometry other) {
        return linearize().difference(other);
    }

    public Geometry symDifference(Geometry other) {
        return linearize().symDifference(other);
    }

    public Geometry union() {
        return linearize().union();
    }

    public Geometry norm() {
        return linearize().norm();
    }

    public int compareTo(Object o) {
        return linearize().compareTo(o);
    }

    public int compareTo(Object o, CoordinateSequenceComparator comp) {
        return linearize().compareTo(o, comp);
    }

    @Override
    public String toText() {
        return linearize().toText();
    }

}
