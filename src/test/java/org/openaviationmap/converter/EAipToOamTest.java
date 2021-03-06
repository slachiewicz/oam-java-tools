/*
    Open Aviation Map
    Copyright (C) 2012-2013 Ákos Maróy

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation, either version 3 of the
    License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.openaviationmap.converter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.openaviationmap.converter.eaip.EAipProcessorAd13;
import org.openaviationmap.model.Aerodrome;
import org.openaviationmap.model.Airspace;
import org.openaviationmap.model.Navaid;
import org.openaviationmap.model.Point;
import org.openaviationmap.model.oam.Action;
import org.openaviationmap.model.oam.Oam;

import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.Vector;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Test cases for testing conversions of specific eAIP sections to OAM.
 */
public class EAipToOamTest {

    /**
     * Test converting an eAIP section to OAM.
     *
     * @param eAipDocumentName the eAIP document to process.
     * @param oamDocumentName the OAM document to verify against.
     * @param borderDocumentName the name of the OAM document describing the
     *        border line.
     * @param ad13DocumentName the name of an aerodrome list eAIP document
     * @param knownErrors the know number of parse errors
     * @param noAirspaces the expected number of airspaces
     * @param noNavaids the expected number of navaids
     * @param noAerodromes the expected number of aerodromes
     * @throws ParserConfigurationException on XML parser configuration errors.
     * @throws IOException on I/O errors
     * @throws SAXException on XML parsing errors
     * @throws ParseException on OAM parsing errors
     * @throws TransformerException on XML serialization errors
     */
    public void testEAipToOam(String eAipDocumentName,
                              String oamDocumentName,
                              String borderDocumentName,
                              String ad13DocumentName,
                              int    knownErrors,
                              int    noAirspaces,
                              int    noNavaids,
                              int    noAerodromes)
                                     throws ParserConfigurationException,
                                            SAXException,
                                            IOException,
                                            ParseException,
                                            TransformerException {

        List<Airspace>       airspaces  = new Vector<Airspace>();
        List<Navaid>         navaids    = new Vector<Navaid>();
        List<Aerodrome>      aerodromes = new Vector<Aerodrome>();
        List<ParseException> errors     = new Vector<ParseException>();


        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder        db  = dbf.newDocumentBuilder();

        // load the border points
        List<Point>    borderPoints = null;

        if (borderDocumentName != null) {
            Document   d   = db.parse(new FileInputStream(borderDocumentName));
            Oam        oam    = new Oam();
            OAMReader  reader = new OAMReader();
            reader.processOsm(d.getDocumentElement(), oam, errors);

            if (!oam.getWays().isEmpty()) {
                // convert the OsmNodes to Points
                List<Integer> refList =
                        oam.getWays().values().iterator().next().getNodeList();

                borderPoints = new Vector<Point>(refList.size());

                for (Integer r : refList) {
                    borderPoints.add(oam.getNodes().get(r).asPoint());
                }
            }
        }

        if (ad13DocumentName != null) {
            Document d = db.parse(new FileInputStream(ad13DocumentName));

            if ("e:AD-1.3".equals(d.getDocumentElement().getTagName())) {
                EAipProcessorAd13 p = new EAipProcessorAd13();
                p.processEAIP(d.getDocumentElement(),
                              borderPoints,
                              airspaces,
                              navaids,
                              aerodromes,
                              errors);
            }

        }

        // first, get an airspace definitions from a eAIP file
        Document   d = db.parse(new FileInputStream(eAipDocumentName));
        EAIPHungaryReader reader   = new EAIPHungaryReader();

        reader.processEAIP(d.getDocumentElement(),
                           borderPoints,
                           airspaces,
                           navaids,
                           aerodromes,
                           errors);

        assertEquals(knownErrors, errors.size());
        assertEquals(noAirspaces, airspaces.size());
        assertEquals(noNavaids, navaids.size());
        assertEquals(noAerodromes, aerodromes.size());

        // reduce the remarks field in airspaces to 255 characters, as
        // the OAM format cannot handle more
        for (Airspace as : airspaces) {
            if (as.getRemarks() != null && as.getRemarks().length() > 255) {
                as.setRemarks(as.getRemarks().substring(0, 255));
            }
        }

        // convert the airspaces into an Oam object
        Oam oam = new Oam();

        OamConverter.airspacesToOam(airspaces, oam, Action.CREATE, 1, 0);
        OamConverter.navaidsToOam(navaids, oam, Action.CREATE, 1,
                                  oam.getMaxNodeId());
        OamConverter.aerodromesToOam(aerodromes, oam, Action.CREATE, 1,
                                     oam.getMaxNodeId());

        // serialize the Oam into a stream
        OAMWriter writer = new OAMWriter();
        d = db.newDocument();
        d = writer.processOam(d, oam);

        TransformerFactory tFactory = TransformerFactory.newInstance();
        Transformer transformer = tFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");

        DOMSource source = new DOMSource(d);
        StringWriter strWriter = new StringWriter();
        StreamResult result = new StreamResult(strWriter);
        transformer.transform(source, result);

        // and now, parse the resulting XML file
        // and compare the two airspace definitions
        StringReader strReader = new StringReader(strWriter.toString());
        InputSource  strSource = new InputSource(strReader);
        d = db.parse(strSource);

        errors.clear();
        OAMReader oamReader = new OAMReader();
        List<Airspace>  oamAirspaces  = new Vector<Airspace>();
        List<Navaid>    oamNavaids    = new Vector<Navaid>();
        List<Aerodrome> oamAerodromes = new Vector<Aerodrome>();
        oamReader.processOam(d.getDocumentElement(),
                             oamAirspaces,
                             oamNavaids,
                             oamAerodromes,
                             errors);

        assertTrue(errors.isEmpty());
        assertEquals(airspaces.size(), oamAirspaces.size());
        assertTrue(airspaces.containsAll(oamAirspaces));
        assertTrue(oamAirspaces.containsAll(airspaces));
        assertEquals(navaids.size(), oamNavaids.size());
        assertTrue(navaids.containsAll(oamNavaids));
        assertTrue(oamNavaids.containsAll(navaids));
        assertEquals(aerodromes.size(), oamAerodromes.size());
        assertTrue(aerodromes.containsAll(oamAerodromes));
        assertTrue(oamAerodromes.containsAll(aerodromes));

        // parse a stored OAM file and compare the airspace definitions
        // with that one as well
        FileReader fReader = new FileReader(oamDocumentName);
        InputSource  fSource = new InputSource(fReader);
        d = db.parse(fSource);

        errors.clear();
        OAMReader fOamReader = new OAMReader();
        List<Airspace>  fOamAirspaces  = new Vector<Airspace>();
        List<Navaid>    fOamNavaids    = new Vector<Navaid>();
        List<Aerodrome> fOamAerodromes = new Vector<Aerodrome>();
        fOamReader.processOam(d.getDocumentElement(),
                              fOamAirspaces,
                              fOamNavaids,
                              fOamAerodromes,
                              errors);

        assertTrue(errors.isEmpty());
        assertEquals(airspaces.size(), fOamAirspaces.size());
        assertTrue(airspaces.containsAll(fOamAirspaces));
        assertTrue(fOamAirspaces.containsAll(airspaces));
        assertEquals(navaids.size(), fOamNavaids.size());
        assertTrue(navaids.containsAll(fOamNavaids));
        assertTrue(fOamNavaids.containsAll(navaids));
        assertEquals(aerodromes.size(), fOamAerodromes.size());
        assertTrue(aerodromes.containsAll(fOamAerodromes));
        assertTrue(fOamAerodromes.containsAll(aerodromes));
    }

    /**
     * Test converting an eAIP section ENR-5.1 element to OAM.
     *
     * @throws ParserConfigurationException on XML parser configuration errors.
     * @throws IOException on I/O errors
     * @throws SAXException on XML parsing errors
     * @throws ParseException on OAM parsing errors
     * @throws TransformerException on XML serialization errors
     */
    @Test
    public void testEAipEnr51ToOam() throws ParserConfigurationException,
                                            SAXException,
                                            IOException,
                                            ParseException,
                                            TransformerException {

        testEAipToOam("var/LH-ENR-5.1-en-HU.xml",
                      "var/oam-hungary-5.1.xml",
                      "var/hungary.osm",
                      "var/LH-AD-1.3-en-HU.xml",
                      4, 47, 0, 0);
    }

    /**
     * Test converting an eAIP section ENR-5.2 element to OAM.
     *
     * @throws ParserConfigurationException on XML parser configuration errors.
     * @throws IOException on I/O errors
     * @throws SAXException on XML parsing errors
     * @throws ParseException on OAM parsing errors
     * @throws TransformerException on XML serialization errors
     */
    @Test
    public void testEAipEnr52ToOam() throws ParserConfigurationException,
                                            SAXException,
                                            IOException,
                                            ParseException,
                                            TransformerException {

        testEAipToOam("var/LH-ENR-5.2-en-HU.xml",
                      "var/oam-hungary-5.2.xml",
                      "var/hungary.osm",
                      "var/LH-AD-1.3-en-HU.xml",
                      0, 34, 0, 0);
    }

    /**
     * Test converting an eAIP section ENR-5.5 element to OAM.
     *
     * @throws ParserConfigurationException on XML parser configuration errors.
     * @throws IOException on I/O errors
     * @throws SAXException on XML parsing errors
     * @throws ParseException on OAM parsing errors
     * @throws TransformerException on XML serialization errors
     */
    @Test
    public void testEAipEnr55ToOam() throws ParserConfigurationException,
                                            SAXException,
                                            IOException,
                                            ParseException,
                                            TransformerException {

        testEAipToOam("var/LH-ENR-5.5-en-HU.xml",
                      "var/oam-hungary-5.5.xml",
                      "var/hungary.osm",
                      "var/LH-AD-1.3-en-HU.xml",
                      0, 15, 0, 0);
    }

    /**
     * Test converting an eAIP section ENR-5.6 element to OAM.
     *
     * @throws ParserConfigurationException on XML parser configuration errors.
     * @throws IOException on I/O errors
     * @throws SAXException on XML parsing errors
     * @throws ParseException on OAM parsing errors
     * @throws TransformerException on XML serialization errors
     */
    @Test
    public void testEAipEnr56ToOam() throws ParserConfigurationException,
                                            SAXException,
                                            IOException,
                                            ParseException,
                                            TransformerException {

        testEAipToOam("var/LH-ENR-5.6-en-HU.xml",
                      "var/oam-hungary-5.6.xml",
                      "var/hungary.osm",
                      "var/LH-AD-1.3-en-HU.xml",
                      0, 37, 0, 0);
    }

    /**
     * Test converting an eAIP section ENR-2.1 element to OAM.
     *
     * @throws ParserConfigurationException on XML parser configuration errors.
     * @throws IOException on I/O errors
     * @throws SAXException on XML parsing errors
     * @throws ParseException on OAM parsing errors
     * @throws TransformerException on XML serialization errors
     */
    @Test
    public void testEAipEnr21ToOam() throws ParserConfigurationException,
                                            SAXException,
                                            IOException,
                                            ParseException,
                                            TransformerException {

        testEAipToOam("var/LH-ENR-2.1-en-HU.xml",
                      "var/oam-hungary-2.1.xml",
                      "var/hungary.osm",
                      "var/LH-AD-1.3-en-HU.xml",
                      0, 18, 0, 0);
    }

    /**
     * Test converting an eAIP section ENR-2.2 element to OAM.
     *
     * @throws ParserConfigurationException on XML parser configuration errors.
     * @throws IOException on I/O errors
     * @throws SAXException on XML parsing errors
     * @throws ParseException on OAM parsing errors
     * @throws TransformerException on XML serialization errors
     */
    @Test
    public void testEAipEnr22ToOam() throws ParserConfigurationException,
                                            SAXException,
                                            IOException,
                                            ParseException,
                                            TransformerException {

        testEAipToOam("var/LH-ENR-2.2-en-HU.xml",
                      "var/oam-hungary-2.2.xml",
                      "var/hungary.osm",
                      "var/LH-AD-1.3-en-HU.xml",
                      0, 3, 0, 0);
    }

    /**
     * Test converting an eAIP section ENR-4.1 element to OAM.
     *
     * @throws ParserConfigurationException on XML parser configuration errors.
     * @throws IOException on I/O errors
     * @throws SAXException on XML parsing errors
     * @throws ParseException on OAM parsing errors
     * @throws TransformerException on XML serialization errors
     */
    @Test
    public void testEAipEnr41ToOam() throws ParserConfigurationException,
                                            SAXException,
                                            IOException,
                                            ParseException,
                                            TransformerException {

        testEAipToOam("var/LH-ENR-4.1-en-HU.xml",
                      "var/oam-hungary-4.1.xml",
                      "var/hungary.osm",
                      "var/LH-AD-1.3-en-HU.xml",
                      1, 0, 18, 0);
    }

    /**
     * Test converting an eAIP section ENR-4.4 element to OAM.
     *
     * @throws ParserConfigurationException on XML parser configuration errors.
     * @throws IOException on I/O errors
     * @throws SAXException on XML parsing errors
     * @throws ParseException on OAM parsing errors
     * @throws TransformerException on XML serialization errors
     */
    @Test
    public void testEAipEnr44ToOam() throws ParserConfigurationException,
                                            SAXException,
                                            IOException,
                                            ParseException,
                                            TransformerException {

        testEAipToOam("var/LH-ENR-4.4-en-HU.xml",
                      "var/oam-hungary-4.4.xml",
                      "var/hungary.osm",
                      "var/LH-AD-1.3-en-HU.xml",
                      0, 0, 81, 0);
    }

    /**
     * Test converting an eAIP section AD-LHBC element to OAM.
     *
     * @throws ParserConfigurationException on XML parser configuration errors.
     * @throws IOException on I/O errors
     * @throws SAXException on XML parsing errors
     * @throws ParseException on OAM parsing errors
     * @throws TransformerException on XML serialization errors
     */
    @Test
    public void testEAipAdLhbcToOam() throws ParserConfigurationException,
                                             SAXException,
                                             IOException,
                                             ParseException,
                                             TransformerException {

        testEAipToOam("var/LH-AD-LHBC-en-HU.xml",
                      "var/oam-hungary-lhbc.xml",
                      "var/hungary.osm",
                      "var/LH-AD-1.3-en-HU.xml",
                      0, 0, 0, 1);
    }

    /**
     * Test converting an eAIP section AD-LHBP element to OAM.
     *
     * @throws ParserConfigurationException on XML parser configuration errors.
     * @throws IOException on I/O errors
     * @throws SAXException on XML parsing errors
     * @throws ParseException on OAM parsing errors
     * @throws TransformerException on XML serialization errors
     */
    @Test
    public void testEAipAdLhbpToOam() throws ParserConfigurationException,
                                             SAXException,
                                             IOException,
                                             ParseException,
                                             TransformerException {

        testEAipToOam("var/LH-AD-LHBP-en-HU.xml",
                      "var/oam-hungary-lhbp.xml",
                      "var/hungary.osm",
                      "var/LH-AD-1.3-en-HU.xml",
                      0, 0, 0, 1);
    }

    /**
     * Test converting an eAIP section AD-LHDC element to OAM.
     *
     * @throws ParserConfigurationException on XML parser configuration errors.
     * @throws IOException on I/O errors
     * @throws SAXException on XML parsing errors
     * @throws ParseException on OAM parsing errors
     * @throws TransformerException on XML serialization errors
     */
    @Test
    public void testEAipAdLhdcToOam() throws ParserConfigurationException,
                                             SAXException,
                                             IOException,
                                             ParseException,
                                             TransformerException {

        testEAipToOam("var/LH-AD-LHDC-en-HU.xml",
                      "var/oam-hungary-lhdc.xml",
                      "var/hungary.osm",
                      "var/LH-AD-1.3-en-HU.xml",
                      0, 0, 0, 1);
    }

    /**
     * Test converting an eAIP section AD-LHFM element to OAM.
     *
     * @throws ParserConfigurationException on XML parser configuration errors.
     * @throws IOException on I/O errors
     * @throws SAXException on XML parsing errors
     * @throws ParseException on OAM parsing errors
     * @throws TransformerException on XML serialization errors
     */
    @Test
    public void testEAipAdLhfmToOam() throws ParserConfigurationException,
                                             SAXException,
                                             IOException,
                                             ParseException,
                                             TransformerException {

        testEAipToOam("var/LH-AD-LHFM-en-HU.xml",
                      "var/oam-hungary-lhfm.xml",
                      "var/hungary.osm",
                      "var/LH-AD-1.3-en-HU.xml",
                      0, 0, 0, 1);
    }

    /**
     * Test converting an eAIP section AD-LHNY element to OAM.
     *
     * @throws ParserConfigurationException on XML parser configuration errors.
     * @throws IOException on I/O errors
     * @throws SAXException on XML parsing errors
     * @throws ParseException on OAM parsing errors
     * @throws TransformerException on XML serialization errors
     */
    @Test
    public void testEAipAdLhnyToOam() throws ParserConfigurationException,
                                             SAXException,
                                             IOException,
                                             ParseException,
                                             TransformerException {

        testEAipToOam("var/LH-AD-LHNY-en-HU.xml",
                      "var/oam-hungary-lhny.xml",
                      "var/hungary.osm",
                      "var/LH-AD-1.3-en-HU.xml",
                      0, 0, 0, 1);
    }

    /**
     * Test converting an eAIP section AD-LHPP element to OAM.
     *
     * @throws ParserConfigurationException on XML parser configuration errors.
     * @throws IOException on I/O errors
     * @throws SAXException on XML parsing errors
     * @throws ParseException on OAM parsing errors
     * @throws TransformerException on XML serialization errors
     */
    @Test
    public void testEAipAdLhppToOam() throws ParserConfigurationException,
                                             SAXException,
                                             IOException,
                                             ParseException,
                                             TransformerException {

        testEAipToOam("var/LH-AD-LHPP-en-HU.xml",
                      "var/oam-hungary-lhpp.xml",
                      "var/hungary.osm",
                      "var/LH-AD-1.3-en-HU.xml",
                      0, 0, 0, 1);
    }

    /**
     * Test converting an eAIP section AD-LHPR element to OAM.
     *
     * @throws ParserConfigurationException on XML parser configuration errors.
     * @throws IOException on I/O errors
     * @throws SAXException on XML parsing errors
     * @throws ParseException on OAM parsing errors
     * @throws TransformerException on XML serialization errors
     */
    @Test
    public void testEAipAdLhprToOam() throws ParserConfigurationException,
                                             SAXException,
                                             IOException,
                                             ParseException,
                                             TransformerException {

        testEAipToOam("var/LH-AD-LHPR-en-HU.xml",
                      "var/oam-hungary-lhpr.xml",
                      "var/hungary.osm",
                      "var/LH-AD-1.3-en-HU.xml",
                      0, 0, 0, 1);
    }

    /**
     * Test converting an eAIP section AD-LHSM element to OAM.
     *
     * @throws ParserConfigurationException on XML parser configuration errors.
     * @throws IOException on I/O errors
     * @throws SAXException on XML parsing errors
     * @throws ParseException on OAM parsing errors
     * @throws TransformerException on XML serialization errors
     */
    @Test
    public void testEAipAdLhsmToOam() throws ParserConfigurationException,
                                             SAXException,
                                             IOException,
                                             ParseException,
                                             TransformerException {

        testEAipToOam("var/LH-AD-LHSM-en-HU.xml",
                      "var/oam-hungary-lhsm.xml",
                      "var/hungary.osm",
                      "var/LH-AD-1.3-en-HU.xml",
                      0, 0, 0, 1);
    }

    /**
     * Test converting an eAIP section AD-LHUD element to OAM.
     *
     * @throws ParserConfigurationException on XML parser configuration errors.
     * @throws IOException on I/O errors
     * @throws SAXException on XML parsing errors
     * @throws ParseException on OAM parsing errors
     * @throws TransformerException on XML serialization errors
     */
    @Test
    public void testEAipAdLhudToOam() throws ParserConfigurationException,
                                             SAXException,
                                             IOException,
                                             ParseException,
                                             TransformerException {

        testEAipToOam("var/LH-AD-LHUD-en-HU.xml",
                      "var/oam-hungary-lhud.xml",
                      "var/hungary.osm",
                      "var/LH-AD-1.3-en-HU.xml",
                      0, 0, 0, 1);
    }
}
