package org.geotools.renderer.crs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.geotools.referencing.operation.projection.PolarStereographic;
import org.geotools.referencing.operation.transform.IdentityTransform;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.crs.SingleCRS;
import org.opengis.referencing.operation.MathTransform;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.CoordinateFilter;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.io.WKTReader;

/**
 * 
 *
 * @source $URL$
 */
public class ProjectionHandlerTest {

    static final double EPS = 1e-6;

    static CoordinateReferenceSystem WGS84;
    
    static CoordinateReferenceSystem ED50_LATLON;

    static CoordinateReferenceSystem UTM32N;

    static CoordinateReferenceSystem MERCATOR;

    static CoordinateReferenceSystem MERCATOR_SHIFTED;

    static CoordinateReferenceSystem ED50;

    @BeforeClass
    public static void setup() throws Exception {
        WGS84 = DefaultGeographicCRS.WGS84;
        UTM32N = CRS.decode("EPSG:32632", true);
        MERCATOR_SHIFTED = CRS.decode("EPSG:3349", true);
        MERCATOR = CRS.decode("EPSG:3395", true);
        ED50 = CRS.decode("EPSG:4230", true);
        ED50_LATLON = CRS.decode("urn:x-ogc:def:crs:EPSG:4230", false);
    }

    @Test
    public void testWrappingOn3DCRS() throws Exception {
        CoordinateReferenceSystem crs = CRS.decode("EPSG:4939", true);
        SingleCRS hcrs = CRS.getHorizontalCRS(crs);
        ReferencedEnvelope wgs84Envelope = new ReferencedEnvelope(-190, 60, -90, 45,
                hcrs);
        ProjectionHandler handler = ProjectionHandlerFinder.getHandler(wgs84Envelope, crs, true);

        assertNull(handler.validAreaBounds);
        List<ReferencedEnvelope> envelopes = handler.getQueryEnvelopes();
        assertEquals(2, envelopes.size());

        ReferencedEnvelope expected = new ReferencedEnvelope(170, 180, -90, 45, hcrs);
        assertTrue(envelopes.remove(wgs84Envelope));
        assertEquals(expected, envelopes.get(0));
    }

    @Test
    public void testQueryWrappingWGS84() throws Exception {
        ReferencedEnvelope wgs84Envelope = new ReferencedEnvelope(-190, 60, -90, 45, WGS84);
        ProjectionHandler handler = ProjectionHandlerFinder.getHandler(wgs84Envelope, WGS84, true);

        assertNull(handler.validAreaBounds);
        List<ReferencedEnvelope> envelopes = handler.getQueryEnvelopes();
        assertEquals(2, envelopes.size());

        ReferencedEnvelope expected = new ReferencedEnvelope(170, 180, -90, 45, WGS84);
        assertTrue(envelopes.remove(wgs84Envelope));
        assertEquals(expected, envelopes.get(0));
    }
    
    @Test
    public void testQueryWrappingED50LatLon() throws Exception {
        ReferencedEnvelope envelope = new ReferencedEnvelope(-90, 45, -190, 60, ED50_LATLON);
        ProjectionHandler handler = ProjectionHandlerFinder.getHandler(envelope, ED50_LATLON, true);

        assertNull(handler.validAreaBounds);
        List<ReferencedEnvelope> envelopes = handler.getQueryEnvelopes();
        assertEquals(2, envelopes.size());

        ReferencedEnvelope expected = new ReferencedEnvelope(170, 180, -90, 45, ED50_LATLON);
        assertTrue(envelopes.remove(envelope));
        assertEquals(expected, envelopes.get(0));
    }

    @Test
    public void testValidAreaMercator() throws Exception {
        ReferencedEnvelope world = new ReferencedEnvelope(-180, 180, -89.9999, 89.9999, WGS84);
        ReferencedEnvelope mercatorEnvelope = world.transform(MERCATOR_SHIFTED, true);

        // check valid area
        ProjectionHandler handler = ProjectionHandlerFinder.getHandler(mercatorEnvelope, WGS84, true);
        Envelope va = handler.validAreaBounds;
        assertNotNull(va);
        assertTrue(va.getMinX() <= -180.0);
        assertTrue(va.getMaxX() >= 180.0);
        assertTrue(-90 < va.getMinY());
        assertTrue(90.0 > va.getMaxY());
    }
    
    @Test
    public void testValidAreaLambertAzimuthalEqualArea() throws Exception {
        // check valid area for the north case
        ReferencedEnvelope wgs84north = new ReferencedEnvelope(-120, 0, 45, 90, WGS84);
        ReferencedEnvelope laeNorth = wgs84north.transform(CRS.decode("EPSG:3408"), true);
        ProjectionHandler handler = ProjectionHandlerFinder.getHandler(laeNorth, WGS84, true);
        Envelope va = handler.validAreaBounds;
        assertNotNull(va);
                assertEquals(-180.0, va.getMinX(), 0d);
        assertEquals(180.0, va.getMaxX(), 0d);
        assertEquals(0, va.getMinY(), 0d);
        assertEquals(90, va.getMaxY(), 0d);
        
        // check the south case
        ReferencedEnvelope wgs84South = new ReferencedEnvelope(-120, 0, -90, -45, WGS84);
        ReferencedEnvelope laeSouth = wgs84South.transform(CRS.decode("EPSG:3409"), true);
        handler = ProjectionHandlerFinder.getHandler(laeSouth, WGS84, true);
        va = handler.validAreaBounds;
        assertNotNull(va);
        assertEquals(-180.0, va.getMinX(), 0d);
        assertEquals(180.0, va.getMaxX(), 0d);
        assertEquals(-90, va.getMinY(), 0d);
        assertEquals(0, va.getMaxY(), 0d);
    }
    
    @Test
    public void testValidAreaLambertConformal() throws Exception {
        // check valid area for the north case
        ReferencedEnvelope wgs84north = new ReferencedEnvelope(-120, 0, 45, 90, WGS84);
        ReferencedEnvelope laeNorth = wgs84north.transform(CRS.decode("EPSG:2062"), true);
        ProjectionHandler handler = ProjectionHandlerFinder.getHandler(laeNorth, WGS84, true);
        Envelope va = handler.validAreaBounds;
        assertNotNull(va);
        assertEquals(-179.9, va.getMinX(), 0d);
        assertEquals(179.9, va.getMaxX(), 0d);
        assertEquals(-4, va.getMinY(), 0d);
        assertEquals(90, va.getMaxY(), 0d);
        
        // check the south case
        ReferencedEnvelope wgs84South = new ReferencedEnvelope(-180, -90, -40, 0, WGS84);
        ReferencedEnvelope laeSouth = wgs84South.transform(CRS.decode("EPSG:2194"), true);
        handler = ProjectionHandlerFinder.getHandler(laeSouth, WGS84, true);
        va = handler.validAreaBounds;
        assertNotNull(va);
        assertEquals(-180, va.getMinX(), 0d);
        assertEquals(180, va.getMaxX(), 0d);
        assertEquals(-90, va.getMinY(), 0d);
        assertEquals(29.73, va.getMaxY(), 0.01d);
    }


    @Test
    public void testQueryWrappingMercatorWorld() throws Exception {
        ReferencedEnvelope world = new ReferencedEnvelope(-200, 200, -89, 89, WGS84);
        ReferencedEnvelope mercatorEnvelope = world.transform(MERCATOR_SHIFTED, true);

        // get query area, we expect just one envelope spanning the whole world
        ProjectionHandler handler = ProjectionHandlerFinder.getHandler(mercatorEnvelope, WGS84, true);
        List<ReferencedEnvelope> envelopes = handler.getQueryEnvelopes();
        assertEquals(1, envelopes.size());

        ReferencedEnvelope env = envelopes.get(0);
        assertEquals(-180.0, env.getMinX(), EPS);
        assertEquals(180.0, env.getMaxX(), EPS);
        assertEquals(-89, env.getMinY(), 0.1);
        assertEquals(89.0, env.getMaxY(), 0.1);
    }

    @Test
    public void testQueryWrappingMercatorSeparate() throws Exception {
        ReferencedEnvelope world = new ReferencedEnvelope(160, 180, -40, 40, WGS84);
        ReferencedEnvelope mercatorEnvelope = world.transform(MERCATOR, true);
        // move it so that it crosses the dateline
        mercatorEnvelope.translate(mercatorEnvelope.getWidth() / 2, 0);

        // get query area, we expect two separate query envelopes, the original and the
        ProjectionHandler handler = ProjectionHandlerFinder.getHandler(mercatorEnvelope, WGS84, true);
        List<ReferencedEnvelope> envelopes = handler.getQueryEnvelopes();
        assertEquals(2, envelopes.size());

        ReferencedEnvelope reOrig = envelopes.get(0); // original
        assertEquals(170.0, reOrig.getMinX(), EPS);
        assertEquals(190.0, reOrig.getMaxX(), EPS);

        ReferencedEnvelope reAdded = envelopes.get(1); // added
        assertEquals(-180.0, reAdded.getMinX(), EPS);
        assertEquals(-170.0, reAdded.getMaxX(), EPS);
    }

    @Test
    public void testValidAreaUTM() throws Exception {
        ReferencedEnvelope wgs84Envelope = new ReferencedEnvelope(8, 10, 40, 45, WGS84);
        ReferencedEnvelope utmEnvelope = wgs84Envelope.transform(UTM32N, true);

        // check valid area
        ProjectionHandler handler = ProjectionHandlerFinder.getHandler(utmEnvelope, WGS84, true);
        Envelope va = handler.validAreaBounds;
        assertNotNull(va);
        assertTrue(9 - 90 < va.getMinX() && va.getMinX() <= 9 - 3);
        assertTrue(9 + 3 <= va.getMaxX() && va.getMaxX() < 9 + 90);
        assertEquals(-90, va.getMinY(), EPS);
        assertEquals(90.0, va.getMaxY(), EPS);
    }

    @Test
    public void testQueryUTM() throws Exception {
        ReferencedEnvelope wgs84Envelope = new ReferencedEnvelope(8, 10, 40, 45, WGS84);
        ReferencedEnvelope utmEnvelope = wgs84Envelope.transform(UTM32N, true);

        // get query area, we expect just one envelope, the original one
        ProjectionHandler handler = ProjectionHandlerFinder.getHandler(utmEnvelope, WGS84, true);
        ReferencedEnvelope expected = utmEnvelope.transform(WGS84, true);
        List<ReferencedEnvelope> envelopes = handler.getQueryEnvelopes();
        assertEquals(1, envelopes.size());
        assertEquals(expected, envelopes.get(0));
    }

    @Test
    public void testWrapGeometryMercator() throws Exception {
        ReferencedEnvelope world = new ReferencedEnvelope(160, 180, -40, 40, WGS84);
        ReferencedEnvelope mercatorEnvelope = world.transform(MERCATOR, true);
        // move it so that it crosses the dateline (measures are still accurate for something
        // crossing the dateline
        mercatorEnvelope.translate(mercatorEnvelope.getWidth() / 2, 0);

        // a geometry that will cross the dateline and sitting in the same area as the
        // rendering envelope
        Geometry g = new WKTReader().read("LINESTRING(170 -40, 190 40)");

        // make sure the geometry is not wrapped
        ProjectionHandler handler = ProjectionHandlerFinder.getHandler(mercatorEnvelope, WGS84, true);
        assertTrue(handler.requiresProcessing( g));
        Geometry preProcessed = handler.preProcess(g);
        // no cutting expected
        assertEquals(g, preProcessed);
        // transform and post process
        MathTransform mt = CRS.findMathTransform(WGS84, MERCATOR, true);
        Geometry transformed = JTS.transform(g, mt);
        Geometry postProcessed = handler.postProcess(mt.inverse(), transformed);
        Envelope env = postProcessed.getEnvelopeInternal();
        // check the geometry is in the same area as the rendering envelope
        assertEquals(mercatorEnvelope.getMinX(), env.getMinX(), EPS);
        assertEquals(mercatorEnvelope.getMaxX(), env.getMaxX(), EPS);
    }
    
    @Test
    public void testWrapGeometrySmall() throws Exception {
        // projected dateline CRS
        CoordinateReferenceSystem FIJI = CRS.decode("EPSG:3460", true);
        // a small geometry that will cross the dateline
        Geometry g = new WKTReader().read("POLYGON ((2139122 5880020, 2139122 5880030, 2139922 5880030, 2139122 5880020))");
        Geometry original = (Geometry) g.clone();

        // rendering bounds only slightly bigger than geometry
        ReferencedEnvelope world = new ReferencedEnvelope(178, 181, -1, 1, WGS84);

        // make sure the geometry is not wrapped, but it is preserved
        ProjectionHandler handler = ProjectionHandlerFinder.getHandler(world, FIJI, true);
        assertTrue(handler.requiresProcessing( g));
        Geometry preProcessed = handler.preProcess(g);
        // no cutting expected
        assertEquals(original, preProcessed);
        // post process
        MathTransform mt = CRS.findMathTransform(FIJI, WGS84);
        Geometry transformed = JTS.transform(g, mt);
        Geometry postProcessed = handler.postProcess(mt.inverse(), transformed);
        // check the geometry is in the same area as the rendering envelope
        assertTrue(world.contains(postProcessed.getEnvelopeInternal()));
    }
    
    @Test
    public void testWorldLargeGeometry() throws Exception {
        ReferencedEnvelope world = new ReferencedEnvelope(-180, 180, -90, 90, WGS84);

        // a geometry close to the dateline 
        Geometry g = new WKTReader()
                .read("POLYGON((-178 -90, -178 90, 178 90, 178 -90, -178 -90))");
        Geometry original = new WKTReader()
                .read("POLYGON((-178 -90, -178 90, 178 90, 178 -90, -178 -90))");

        // make sure the geometry is not wrapped, but it is preserved
        ProjectionHandler handler = ProjectionHandlerFinder.getHandler(world, WGS84, true);
        assertTrue(handler.requiresProcessing(g));
        Geometry preProcessed = handler.preProcess(g);
        // no cutting expected
        assertEquals(original, preProcessed);
        // post process (provide identity transform to force wrap heuristic)
        Geometry postProcessed = handler.postProcess(CRS.findMathTransform(WGS84, WGS84), g);
        // check the geometry is in the same area as the rendering envelope
        assertEquals(original, postProcessed);
    }
    
    @Test
    public void testWrapGeometryLatLonMultipleTimes() throws Exception {
        ReferencedEnvelope renderingEnvelope = new ReferencedEnvelope(-90, 90, -580, 540, ED50_LATLON);

        // a geometry close to the dateline 
        Geometry g = new WKTReader()
                .read("POLYGON((-74 -33, -29 -33, -29 5, -74 5, -74 -33))");

        // make sure the geometry is not wrapped, but it is preserved
        ProjectionHandler handler = ProjectionHandlerFinder.getHandler(renderingEnvelope, WGS84, true);
        assertTrue(handler.requiresProcessing(g));
        Geometry preProcessed = handler.preProcess(g);
        MathTransform mt = handler.getRenderingTransform(CRS.findMathTransform(WGS84, ED50_LATLON));
        Geometry transformed = JTS.transform(preProcessed, mt);
        // post process (provide identity transform to force wrap heuristic)
        Geometry postProcessed = handler.postProcess(mt, transformed);
        assertTrue(postProcessed.isValid());
        // should have been replicated three times
        assertEquals(3, postProcessed.getNumGeometries());
    }
    
    @Test
    public void testWrapGeometryReprojectToLatLonED50() throws Exception {
        ReferencedEnvelope world = new ReferencedEnvelope(-80, 80, -180, 180, ED50_LATLON);

        // make sure the geometry is not wrapped, but it is preserved
        ProjectionHandler handler = ProjectionHandlerFinder.getHandler(world, WGS84, true);

        // a geometry that will cross the dateline and sitting in the same area as the
        // rendering envelope (with wgs84 lon/latcoordinates)
        String wkt = "POLYGON((178 -80, 178 80, 182 80, 182 80, 178 -80))";
        Geometry g = new WKTReader().read(wkt);
        Geometry original = new WKTReader().read(wkt);
        MathTransform mt = CRS.findMathTransform(WGS84, ED50_LATLON);
        MathTransform prepared = handler.getRenderingTransform(CRS.findMathTransform(WGS84, ED50_LATLON));
        Geometry reprojected = JTS.transform(original, prepared);

        
        assertTrue(handler.requiresProcessing( g));
        Geometry preProcessed = handler.preProcess(g);
        // no cutting expected
        assertEquals(original, preProcessed);
        // post process, this should wrap the geometry and clone it
        Geometry postProcessed = handler.postProcess(prepared, reprojected);
        assertTrue(postProcessed instanceof MultiPolygon);
    }
    
    @Test
    public void testWrapAnctartica() throws Exception {
        ReferencedEnvelope world = new ReferencedEnvelope(-80, 80, -180, 180, ED50_LATLON);

        // make sure the geometry is not wrapped, but it is preserved
        ProjectionHandler handler = ProjectionHandlerFinder.getHandler(world, WGS84, true);

        // a geometry that will cross the dateline and sitting in the same area as the
        // rendering envelope (with wgs84 lon/latcoordinates)
        String wkt = "POLYGON((180 -90, 180 90, -180 90, -180 -90, 180 -90))";
        Geometry g = new WKTReader().read(wkt);
        MathTransform mt = CRS.findMathTransform(WGS84, ED50_LATLON);
        MathTransform prepared = handler.getRenderingTransform(mt);

        
        assertTrue(handler.requiresProcessing( g));
        Geometry preProcessed = handler.preProcess(g);
        Geometry reprojected = JTS.transform(preProcessed, prepared);
        assertTrue(reprojected.isValid());
        reprojected.apply(new CoordinateFilter() {
            
            @Override
            public void filter(Coordinate coord) {
                assertEquals(90.0, Math.abs(coord.getOrdinate(0)), 0.1);
                assertEquals(180.0, Math.abs(coord.getOrdinate(1)), 5);
            }
        });
        // post process, this should wrap the geometry, make sure it's valid, and avoid large jumps in its border
        Geometry postProcessed = handler.postProcess(prepared, reprojected);
        assertTrue(postProcessed instanceof MultiPolygon);
        assertEquals(2, postProcessed.getNumGeometries());
    }
    
    @Test
    public void testWrapGeometryReprojectToED50() throws Exception {
        ReferencedEnvelope world = new ReferencedEnvelope(-80, 80, -180, 180, ED50);
        ProjectionHandler handler = ProjectionHandlerFinder.getHandler(world, WGS84, true);
        
        // a geometry that will cross the dateline and sitting in the same area as the
        // rendering envelope (with wgs84 lon/latcoordinates)
        String wkt = "POLYGON((178 -80, 178 80, 182 80, 182 80, 178 -80))";
        Geometry g = new WKTReader().read(wkt);
        Geometry original = new WKTReader().read(wkt);
        MathTransform mt = CRS.findMathTransform(WGS84, ED50);
        mt = handler.getRenderingTransform(mt);
        Geometry reprojected = JTS.transform(original, mt);

        // make sure the geometry is not wrapped, but it is preserved
        
        assertTrue(handler.requiresProcessing( g));
        Geometry preProcessed = handler.preProcess(g);
        // no cutting expected
        assertEquals(original, preProcessed);
        // post process, this should wrap the geometry and clone it
        Geometry postProcessed = handler.postProcess(mt, reprojected);
        assertTrue(postProcessed instanceof MultiPolygon);
    }
    
    @Test
    public void testWrapJumpLast() throws Exception {
        ReferencedEnvelope world = new ReferencedEnvelope(-180, 180, -90, 90, WGS84);
        Geometry g = new WKTReader().read("POLYGON((-131 -73.5,0 -90,163 -60,174 -60,-131 -73.5))");
        Geometry original = new WKTReader().read("POLYGON((-131 -73.5,0 -90,163 -60,174 -60,-131 -73.5))");
        // make sure the geometry is not wrapped, but it is preserved
        ProjectionHandler handler = ProjectionHandlerFinder.getHandler(world, WGS84, true);
        assertTrue(handler.requiresProcessing(g));
        Geometry preProcessed = handler.preProcess(g);
        // no cutting expected
        assertEquals(original, preProcessed);
        // post process (provide identity transform to force wrap heuristic)
        Geometry postProcessed = handler.postProcess(CRS.findMathTransform(WGS84, WGS84), g);
        // check the geometry is in the same area as the rendering envelope
        assertEquals(original, postProcessed);
    }
    
    @Test
    public void testWrapGeometryWGS84Duplicate() throws Exception {
        ReferencedEnvelope world = new ReferencedEnvelope(-200, 200, -90, 90, WGS84);

        // a geometry that will cross the dateline and sitting in the same area as the
        // rendering envelope
        Geometry g = new WKTReader().read("POLYGON((-178 -90, -178 90, 178 90, 178 -90, -178 -90))");
        Geometry original = new WKTReader().read("POLYGON((-178 -90, -178 90, 178 90, 178 -90, -178 -90))");

        // make sure the geometry is not wrapped, but it is preserved
        ProjectionHandler handler = ProjectionHandlerFinder.getHandler(world, WGS84, true);
        assertTrue(handler.requiresProcessing(g));
        Geometry preProcessed = handler.preProcess(g);
        // no cutting expected
        assertEquals(original, preProcessed);
        // post process
        Geometry postProcessed = handler.postProcess(null, g);                
        // check we have replicated the geometry in both directions
        Envelope ppEnvelope = postProcessed.getEnvelopeInternal();
        Envelope expected = new Envelope(-538, 538, -90, 90);
        assertEquals(expected, ppEnvelope);
    }


    @Test
    public void testDuplicateGeometryMercator() throws Exception {
        ReferencedEnvelope world = new ReferencedEnvelope(-180, 180, -50, 50, WGS84);
        ReferencedEnvelope mercatorEnvelope = world.transform(MERCATOR, true);

        // a geometry that will cross the dateline and sitting in the same area as the
        // rendering envelope
        Geometry g = new WKTReader().read("LINESTRING(170 -50, 190 50)");

        // make sure the geometry is not wrapped
        ProjectionHandler handler = ProjectionHandlerFinder.getHandler(mercatorEnvelope, WGS84, true);
        assertTrue(handler.requiresProcessing(g));
        Geometry preProcessed = handler.preProcess(g);
        // no cutting expected
        assertEquals(g, preProcessed);
        // transform and post process
        MathTransform mt = CRS.findMathTransform(WGS84, MERCATOR, true);
        Geometry transformed = JTS.transform(g, mt);
        Geometry postProcessed = handler.postProcess(mt, transformed);
        // should have been duplicated in two parts
        assertTrue(postProcessed instanceof MultiLineString);
        MultiLineString mls = (MultiLineString) postProcessed;
        assertEquals(2, mls.getNumGeometries());
        // the two geometries width should be the same as 20°
        double twentyDegWidth = mercatorEnvelope.getWidth() / 18;
        assertEquals(twentyDegWidth, mls.getGeometryN(0).getEnvelopeInternal().getWidth(), EPS);
        assertEquals(twentyDegWidth, mls.getGeometryN(1).getEnvelopeInternal().getWidth(), EPS);
    }
    
    @Test
    public void testLimitExcessiveDuplication() throws Exception {
        // a veeeery large rendering envelope, enough to trigger whatever are the default 
        // protection limits (yes, it's in degrees!)
        ReferencedEnvelope renderingEnvelope = new ReferencedEnvelope(-1800000, 1800000, -50, 50, WGS84);

        // the geometry that will be wrapped
        Geometry g = new WKTReader().read("LINESTRING(-179 -89, 179 89)");

        // make sure the geometry is not pre-processed
        ProjectionHandler handler = ProjectionHandlerFinder.getHandler(renderingEnvelope, WGS84, true);
        assertTrue(handler.requiresProcessing(g));
        Geometry preProcessed = handler.preProcess(g);
        assertEquals(g, preProcessed);
        Geometry postProcessed = handler.postProcess(IdentityTransform.create(2), g);
        // should have been copied several times, but not above the limit
        assertTrue(postProcessed instanceof MultiLineString);
        MultiLineString mls = (MultiLineString) postProcessed;
        assertEquals(ProjectionHandlerFinder.WRAP_LIMIT * 2 + 1 , mls.getNumGeometries());
    }

    public void testCutGeometryUTM() throws Exception {
        ReferencedEnvelope wgs84Envelope = new ReferencedEnvelope(8, 10, 40, 45, WGS84);
        ReferencedEnvelope utmEnvelope = wgs84Envelope.transform(UTM32N, true);

        // a geometry that will definitely go outside of the UTM32N valid area
        Geometry g = new WKTReader().read("LINESTRING(-170 -40, 170, 40)");

        ProjectionHandler handler = ProjectionHandlerFinder.getHandler(utmEnvelope, WGS84, true);
        assertTrue(handler.requiresProcessing(g));
        Geometry preProcessed = handler.preProcess(g);
        assertTrue(!preProcessed.equalsTopo(g));
        assertTrue(handler.validAreaBounds.contains(preProcessed.getEnvelopeInternal()));
    }

    @Test
    public void testPolarStereographic() throws Exception {
        ReferencedEnvelope envelope = new ReferencedEnvelope(-10700000, 14700000, -10700000,
                14700000, CRS.decode("EPSG:5041", true));
        ProjectionHandler handler = ProjectionHandlerFinder.getHandler(envelope, WGS84, true);
        assertNotNull(handler);
        assertEquals(envelope, handler.getRenderingEnvelope());
        assertTrue(CRS.getMapProjection(envelope.getCoordinateReferenceSystem()) instanceof PolarStereographic);
    }

}
