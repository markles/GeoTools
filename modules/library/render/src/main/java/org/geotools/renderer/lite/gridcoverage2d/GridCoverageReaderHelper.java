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
package org.geotools.renderer.lite.gridcoverage2d;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.media.jai.Interpolation;
import javax.media.jai.InterpolationNearest;

import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridEnvelope2D;
import org.geotools.coverage.grid.GridGeometry2D;
import org.geotools.coverage.grid.io.AbstractGridFormat;
import org.geotools.coverage.grid.io.GridCoverage2DReader;
import org.geotools.coverage.grid.io.ReadResolutionCalculator;
import org.geotools.coverage.processing.CoverageProcessor;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.parameter.Parameter;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.geotools.renderer.crs.ProjectionHandler;
import org.geotools.util.logging.Logging;
import org.opengis.coverage.processing.Operation;
import org.opengis.geometry.BoundingBox;
import org.opengis.parameter.GeneralParameterValue;
import org.opengis.parameter.ParameterValueGroup;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.datum.PixelInCell;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.MathTransform2D;
import org.opengis.referencing.operation.TransformException;

import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Polygon;

/**
 * Support class that performs the actions needed to read a GridCoverage for the task of rendering
 * it at a given resolution, on a given area, taking into account projection oddities, dateline
 * crossing, and the like
 * 
 * @author Andrea Aime - GeoSolutions
 */
public class GridCoverageReaderHelper {

    private static final CoverageProcessor PROCESSOR = CoverageProcessor.getInstance();

    private static final Operation CROP = PROCESSOR.getOperation("CoverageCrop");

    private static final int DEFAULT_PADDING = 10;

    private static final Logger LOGGER = Logging.getLogger(GridCoverageReaderHelper.class);

    private GridCoverage2DReader reader;

    private ReferencedEnvelope mapExtent;

    private Rectangle mapRasterArea;

    private MathTransform worldToScreen;

    private GridGeometry2D requestedGridGeometry;

    private boolean paddingRequired;

    private boolean sameCRS;

    public GridCoverageReaderHelper(GridCoverage2DReader reader, Rectangle mapRasterArea,
            ReferencedEnvelope mapExtent, Interpolation interpolation) throws FactoryException {
        this.reader = reader;
        this.mapExtent = mapExtent;
        this.requestedGridGeometry = new GridGeometry2D(new GridEnvelope2D(mapRasterArea),
                mapExtent);
        this.worldToScreen = requestedGridGeometry.getCRSToGrid2D();

        // determine if we need a reading gutter, or not, we do if we are reprojecting, or if
        // there is an interpolation to be applied, in that case we need to expand the area
        // we are going to read
        sameCRS = CRS.equalsIgnoreMetadata(mapExtent.getCoordinateReferenceSystem(),
                reader.getCoordinateReferenceSystem());
        paddingRequired = !sameCRS || !(interpolation instanceof InterpolationNearest);
        if (paddingRequired) {
            // expand the map raster area
            GridEnvelope2D requestedGridEnvelope = new GridEnvelope2D(mapRasterArea);
            applyReadGutter(requestedGridEnvelope);

            // now create the final envelope accordingly
            try {
                this.requestedGridGeometry = new GridGeometry2D(requestedGridEnvelope,
                        PixelInCell.CELL_CORNER, worldToScreen.inverse(),
                        mapExtent.getCoordinateReferenceSystem(), null);
                this.mapExtent = ReferencedEnvelope
                        .reference(requestedGridGeometry.getEnvelope2D());
                this.mapRasterArea = requestedGridGeometry.getGridRange2D().getBounds();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            this.mapExtent = mapExtent;
            this.mapRasterArea = mapRasterArea;
        }
    }

    public ReferencedEnvelope getReadEnvelope() {
        return mapExtent;
    }

    private void applyReadGutter(GridEnvelope2D gridRange) {
        gridRange.setBounds(gridRange.x - DEFAULT_PADDING, gridRange.y - DEFAULT_PADDING,
                gridRange.width + DEFAULT_PADDING * 2, gridRange.height + DEFAULT_PADDING * 2);
    }

    private GridGeometry2D applyReadGutter(GridGeometry2D gg) {
        MathTransform gridToCRS = gg.getGridToCRS();
        GridEnvelope2D range = new GridEnvelope2D(gg.getGridRange2D());
        applyReadGutter(range);
        CoordinateReferenceSystem crs = gg.getEnvelope2D().getCoordinateReferenceSystem();
        GridGeometry2D result = new GridGeometry2D(range, PixelInCell.CELL_CORNER, gridToCRS, crs,
                null);

        return result;

    }

    /**
     * Reads a single coverage for the area specified in the constructor, the code will not attempt
     * multiple reads to manage reads across the date line, reducing the read area, splitting it
     * into parts to manage certain projections (e.g., conic) and so on
     */
    public GridCoverage2D readCoverage(final GeneralParameterValue[] params) throws IOException {
        return readSingleCoverage(params, requestedGridGeometry);
    }

    /**
     * Reads the data taking into account advanced projection handling in order to deal with date
     * line crossing, poles and other projection trouble areas. The result is a set of coverages
     * that can be either painted or reprojected safely
     * 
     * @param params
     * @return
     * @throws IOException
     * @throws FactoryException
     * @throws TransformException
     */
    public List<GridCoverage2D> readCoverages(final GeneralParameterValue[] readParams,
            ProjectionHandler handler)
            throws IOException, FactoryException, TransformException {
        if (handler == null) {
            GridCoverage2D readCoverage = readCoverage(readParams);
            return Arrays.asList(readCoverage);
        }

        GridGeometry2D gg = new GridGeometry2D(new GridEnvelope2D(mapRasterArea), mapExtent);

        CoordinateReferenceSystem readerCRS = reader.getCoordinateReferenceSystem();

        // get the areas that we are likely to have to read, and have the projection
        // handler also cut them
        List<GridCoverage2D> coverages = new ArrayList<GridCoverage2D>();
        PolygonExtractor polygonExtractor = new PolygonExtractor();
        for (ReferencedEnvelope envelope : handler.getQueryEnvelopes()) {
            Polygon polygon = JTS.toGeometry(envelope);

            GridGeometry2D readingGridGeometry = computeReadingGeometry(gg, readerCRS, polygon, handler);
            if (readingGridGeometry == null) {
                continue;
            }
            if (paddingRequired) {
                readingGridGeometry = applyReadGutter(readingGridGeometry);
            }
            GridCoverage2D coverage = readSingleCoverage(readParams, readingGridGeometry);
            if (coverage == null) {
                continue;
            }

            // cut and slice the geometry as required by the projection handler
            ReferencedEnvelope readingEnvelope = ReferencedEnvelope.reference(readingGridGeometry
                    .getEnvelope2D());
            ReferencedEnvelope coverageEnvelope = ReferencedEnvelope.reference(coverage
                    .getEnvelope2D());
            Polygon coverageFootprint = JTS.toGeometry(coverageEnvelope);
            Geometry preProcessed = handler.preProcess(coverageFootprint);
            if (preProcessed == null || preProcessed.isEmpty()) {
                continue;
            } else if (coverageFootprint.equals(preProcessed)) {
                // we might still have read more than requested
                if(!readingEnvelope.contains((Envelope) coverageEnvelope)) {
                    ReferencedEnvelope cropEnvelope = new ReferencedEnvelope(
                            readingEnvelope.intersection(coverageEnvelope), readerCRS);
                    if (isNotEmpty(cropEnvelope)) {
                        GridCoverage2D cropped = cropCoverage(coverage, cropEnvelope);
                        coverages.add(cropped);
                    }
                } else {
                    coverages.add(coverage);
                }
            } else {
                final List<Polygon> polygons = polygonExtractor.getPolygons(preProcessed);
                for (Polygon p : polygons) {
                    ReferencedEnvelope cropEnvelope = new ReferencedEnvelope(
                            p.getEnvelopeInternal(), readerCRS);
                    cropEnvelope = new ReferencedEnvelope(
                            cropEnvelope.intersection(coverageEnvelope), readerCRS);
                    cropEnvelope = new ReferencedEnvelope(
                            cropEnvelope.intersection(readingEnvelope), readerCRS);
                    if (isNotEmpty(cropEnvelope)) {
                        GridCoverage2D cropped = cropCoverage(coverage, cropEnvelope);
                        coverages.add(cropped);
                    }
                }

            }

        }

        return coverages;
    }

    private boolean isNotEmpty(ReferencedEnvelope envelope) {
        return !envelope.isEmpty() && !envelope.isNull() && envelope.getWidth() > 0
                && envelope.getHeight() > 0;
    }

    private GridCoverage2D cropCoverage(GridCoverage2D coverage, ReferencedEnvelope cropEnvelope) {
        final ParameterValueGroup param = CROP.getParameters();
        param.parameter("Source").setValue(coverage);
        param.parameter("Envelope").setValue(cropEnvelope);

        GridCoverage2D cropped = (GridCoverage2D) PROCESSOR.doOperation(param);
        return cropped;
    }

    private GridGeometry2D computeReadingGeometry(GridGeometry2D gg,
            CoordinateReferenceSystem readerCRS, Polygon polygon, ProjectionHandler handler)
            throws TransformException,
            FactoryException, IOException {
        GridGeometry2D readingGridGeometry;
        MathTransform2D crsToGrid2D = gg.getCRSToGrid2D();
        MathTransform2D gridToCRS2D = gg.getGridToCRS2D();
        if (sameCRS) {
            Envelope gridEnvelope = JTS.transform(polygon, crsToGrid2D).getEnvelopeInternal();
            GridEnvelope2D gridRange = new GridEnvelope2D((int) gridEnvelope.getMinX(),
                    (int) gridEnvelope.getMinY(), (int) gridEnvelope.getWidth(),
                    (int) gridEnvelope.getHeight());
            readingGridGeometry = new GridGeometry2D(gridRange, gridToCRS2D, readerCRS);
        } else {
            ReferencedEnvelope readEnvelope = new ReferencedEnvelope(polygon.getEnvelopeInternal(),
                    readerCRS);
            // while we want to read as much data as possible, and cut it only later
            // to avoid warping edge effects later, the resolution needs to be
            // computed against an area that's sane for the projection at hand
            ReferencedEnvelope reducedEnvelope = reduceEnvelope(readEnvelope, handler);
            if (reducedEnvelope == null) {
                return null;
            }
            ReferencedEnvelope reducedEnvelopeInRequestedCRS = reducedEnvelope.transform(
                    requestedGridGeometry.getCoordinateReferenceSystem(), true);
            ReferencedEnvelope gridEnvelope = ReferencedEnvelope.reference(CRS.transform(
                    crsToGrid2D, reducedEnvelopeInRequestedCRS));
            GridEnvelope2D readingGridRange = new GridEnvelope2D((int) gridEnvelope.getMinX(),
                    (int) gridEnvelope.getMinY(), (int) gridEnvelope.getWidth(),
                    (int) gridEnvelope.getHeight());
            GridGeometry2D localGridGeometry = new GridGeometry2D(readingGridRange, gridToCRS2D,
                    mapExtent.getCoordinateReferenceSystem());

            double[][] resolutionLevels = reader.getResolutionLevels();
            ReadResolutionCalculator calculator = new ReadResolutionCalculator(localGridGeometry,
                    readerCRS, resolutionLevels != null ? resolutionLevels[0] : null);
            calculator.setAccurateResolution(true);
            double[] readResolution = calculator.computeRequestedResolution(reducedEnvelope);
            int width = (int) Math.max(1,
                    Math.round(readEnvelope.getWidth() / Math.abs(readResolution[0])));
            int height = (int) Math.max(1,
                    Math.round(readEnvelope.getHeight() / Math.abs(readResolution[1])));
            GridEnvelope2D gridRange = new GridEnvelope2D(0, 0, width, height);
            readingGridGeometry = new GridGeometry2D(gridRange, readEnvelope);
        }
        return readingGridGeometry;
    }

    private ReferencedEnvelope reduceEnvelope(ReferencedEnvelope envelope, ProjectionHandler handler)
            throws TransformException, FactoryException {
        Polygon polygon = JTS.toGeometry(envelope);
        Geometry geom = handler.preProcess(polygon);
        if (geom == null) {
            return null;
        }
        PolygonExtractor pe = new PolygonExtractor();
        Polygon largest = null;
        for (Polygon p : pe.getPolygons(geom)) {
            if (largest == null || largest.getArea() > p.getArea()) {
                largest = p;
            }
        }

        ReferencedEnvelope reduced = new ReferencedEnvelope(largest.getEnvelopeInternal(),
                envelope.getCoordinateReferenceSystem());
        return reduced;
    }

    /**
     * Reads a single coverage given the specified read parameters and the grid geometry
     * 
     * @param readParams (might be null)
     * @param gg
     * @return
     * @throws IOException
     */
    private GridCoverage2D readSingleCoverage(GeneralParameterValue[] readParams, GridGeometry2D gg)
            throws IOException {
        // //
        //
        // Intersect the present envelope with the request envelope, also in WGS 84 to make sure
        // there is an actual intersection
        //
        // //
        boolean sameCRS;
        ReferencedEnvelope requestedEnvelope = ReferencedEnvelope.reference(gg.getEnvelope2D());
        try {
            final CoordinateReferenceSystem coverageCRS = reader.getCoordinateReferenceSystem();
            final CoordinateReferenceSystem requestCRS = gg.getCoordinateReferenceSystem();
            final ReferencedEnvelope coverageEnvelope = ReferencedEnvelope.reference(reader
                    .getOriginalEnvelope());
            final ReferencedEnvelope readEnvelope = requestedEnvelope;
            sameCRS = CRS.equalsIgnoreMetadata(coverageCRS, requestCRS);
            if (sameCRS) {
                if (!coverageEnvelope.intersects((BoundingBox) readEnvelope)) {
                    return null;
                }
            } else {
                ReferencedEnvelope dataEnvelopeWGS84 = coverageEnvelope.transform(
                        DefaultGeographicCRS.WGS84, true);
                ReferencedEnvelope requestEnvelopeWGS84 = readEnvelope.transform(
                        DefaultGeographicCRS.WGS84, true);
                if (!dataEnvelopeWGS84.intersects((BoundingBox) requestEnvelopeWGS84)) {
                    return null;
                }
            }
        } catch (Exception e) {
            LOGGER.log(
                    Level.WARNING,
                    "Failed to compare data and request envelopes, reading the whole mapExtent instead",
                    e);
        }

        // setup the grid geometry param that will be passed to the reader
        final Parameter<GridGeometry2D> readGGParam = (Parameter<GridGeometry2D>) AbstractGridFormat.READ_GRIDGEOMETRY2D
                .createValue();
        readGGParam.setValue(new GridGeometry2D(gg));

        // then I try to get read parameters associated with this
        // coverage if there are any.
        GridCoverage2D coverage = null;
        if (readParams != null) {
            // //
            //
            // Getting parameters to control how to read this coverage.
            // Remember to check to actually have them before forwarding
            // them to the reader.
            //
            // //
            final int length = readParams.length;
            if (length > 0) {
                // we have a valid number of parameters, let's check if
                // also have a READ_GRIDGEOMETRY2D. In such case we just
                // override it with the one we just build for this
                // request.
                final String name = AbstractGridFormat.READ_GRIDGEOMETRY2D.getName().toString();
                int i = 0;
                for (; i < length; i++) {
                    if (readParams[i].getDescriptor().getName().toString().equalsIgnoreCase(name))
                        break;
                }
                // did we find anything?
                if (i < length) {
                    // we found another READ_GRIDGEOMETRY2D, let's override it.
                    readParams[i] = readGGParam;
                    coverage = reader.read(readParams);
                } else {
                    // add the correct read geometry to the supplied
                    // params since we did not find anything
                    GeneralParameterValue[] readParams2 = new GeneralParameterValue[length + 1];
                    System.arraycopy(readParams, 0, readParams2, 0, length);
                    readParams2[length] = readGGParam;
                    coverage = reader.read(readParams2);
                }
            } else
                // we have no parameters hence we just use the read grid
                // geometry to get a coverage
                coverage = reader.read(new GeneralParameterValue[] { readGGParam });
        } else if (gg != null) {
            coverage = reader.read(new GeneralParameterValue[] { readGGParam });
        } else {
            coverage = reader.read(null);
        }

        return coverage;
    }

}
