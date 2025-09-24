package de.planyverse.xfdf_annotation_merge;

import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.action.PdfAction;
import com.itextpdf.kernel.pdf.annot.*;
import com.itextpdf.kernel.pdf.colorspace.PdfColorSpace;
import com.itextpdf.kernel.pdf.xobject.PdfFormXObject;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

@Service
public class XfdfService {
    public void mergeXfdfIntoPdf() throws Exception {
        String pdfForm = "src/main/resources/sample.pdf";
        String xfdf1 = "src/main/resources/sample1.xfdf";
        String xfdf2 = "src/main/resources/sample2.xfdf";
        String xfdf3 = "src/main/resources/sample3.xfdf";
        String xfdf4 = "src/main/resources/sample4.xfdf";
        String dest = "src/main/resources/output.pdf";

        PdfDocument pdfDocument = new PdfDocument(
                new PdfReader(new FileInputStream(pdfForm)),
                new PdfWriter(new FileOutputStream(dest))
        );

        PdfPage page = pdfDocument.getPage(1);

        addXFDF(page, xfdf1);
        addXFDF(page, xfdf2);
        addXFDF(page, xfdf3);
        addXFDF(page, xfdf4);

        pdfDocument.close();
    }

    private static void addXFDF(PdfPage page, String path) throws Exception {
        File xmlFile = new File(path);
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(xmlFile);

        Node annots = doc.getElementsByTagName("annots").item(0);
        NodeList annotations = annots.getChildNodes();

        for(int i = 0; i < annotations.getLength(); i++) {
            if(!(annotations.item(i) instanceof Element element)) continue; // skip text nodes

            PdfAnnotation annotation = null;
            var rect = getRectValues(element);

            if(rect == null) continue; // rect is mandatory

            switch (element.getNodeName()) {
                case "text" -> {
                    annotation = new PdfTextAnnotation(rect)
                            .setOpen(true)
                            .setContents(getElementTextContent(element, "contents"));
                }
                case "stamp" -> {
                    var content = getElementTextContent(element, "appearance");
                    if(content != null) {
                        byte[] bytes = Base64.getDecoder().decode(content);
                        PdfStream stream = new PdfStream(bytes);
                        var xObject = new PdfFormXObject(stream);
                        annotation = new PdfStampAnnotation(rect)
                                .setNormalAppearance(xObject.getPdfObject());
                        }
                }
                case "circle" -> {
                    annotation = new PdfCircleAnnotation(rect);
                    setElementMetadata(element, annotation);
                }
                case "polyline" -> {
                    var content = getElementTextContent(element, "vertices");
                    if(content != null) {
                        String[] parts = content.split("[,;]");
                        float[] floatValues = getFloatValues(parts);
                        annotation = PdfPolyGeomAnnotation.createPolyLine(rect, floatValues);
                        setElementMetadata(element, annotation);
                    }
                }
                case "highlight" -> {
                    var coords = getAttributeTextContent(element, "coords").split(",");
                    float[] floatValues = getFloatValues(coords);
                    annotation = new PdfTextMarkupAnnotation(rect, PdfName.Highlight, floatValues);
                    setElementMetadata(element, annotation);
                }
                case "freetext" -> {
                    var text = new PdfString(getElementTextContent(element, "contents"));
                    annotation = new PdfFreeTextAnnotation(rect, text);
                    var defaultAppearance = getAttributeTextContent(element, "defaultappearance");
                    if(defaultAppearance != null) {
                        ((PdfFreeTextAnnotation)annotation).setDefaultAppearance(new PdfString(defaultAppearance));
                    }
                    setElementMetadata(element, annotation);
                }
                case "link" -> {
                    var uri = getAttributeTextContent(element, "target");
                    annotation = new PdfLinkAnnotation(rect)
                            .setAction(PdfAction.createURI(uri));
                    setElementMetadata(element, annotation);
                }
                case "line" -> {
                    var start = getAttributeTextContent(element, "start");
                    var end = getAttributeTextContent(element, "end");
                    if(start != null && end != null) {
                        annotation = new PdfLineAnnotation(rect, new float[] {
                                Float.parseFloat(start.split(",")[0].trim()),
                                Float.parseFloat(start.split(",")[1].trim()),
                                Float.parseFloat(end.split(",")[0].trim()),
                                Float.parseFloat(end.split(",")[1].trim())
                        });
                        setElementMetadata(element, annotation);
                    }
                }
                case "ink" -> {
                    PdfArray inkList = new PdfArray();
                    var gestures = element.getElementsByTagName("gesture");
                    for(int g = 0; g < gestures.getLength(); g++) {
                        String[] pairs = gestures.item(g).getTextContent().trim().split(";");
                        PdfArray gesture = new PdfArray();
                        for (String pair : pairs) {
                            String[] xy = pair.trim().split(",");
                            if (xy.length == 2) {
                                gesture.add(new PdfNumber(Float.parseFloat(xy[0])));
                                gesture.add(new PdfNumber(Float.parseFloat(xy[1])));
                            }
                        }
                        inkList.add(gesture);
                    }
                    annotation = new PdfInkAnnotation(rect, inkList);
                    NodeList intensityList = element.getElementsByTagName("pspdf-intensity");
                    if (intensityList.getLength() > 0) {
                        String[] intensities = intensityList.item(0).getTextContent().split(";");
                        if (intensities.length > 0) {
                            ((PdfInkAnnotation)annotation).setOpacity(new PdfNumber(Float.parseFloat(intensities[0])));
                        }
                    }
                    setElementMetadata(element, annotation);
                }
            }

            if(annotation != null) page.addAnnotation(annotation);
        }
    }

    private static void setElementMetadata(Node element, PdfAnnotation annotation) {
        var contents = getElementTextContent(element, "contents");
        if (contents != null) annotation.setContents(contents);

        var color = getColor(element);
        if (color != null) annotation.setColor(color);

        var width = getAttributeTextContent(element, "width");
        if (width != null) {
            var borderStyle = new int[] {0,0,(int)Float.parseFloat(width)};
            annotation.setBorder(new PdfArray(borderStyle));
        }

        var flags = getAttributeTextContent(element, "flags");
        if (flags != null && flags.equalsIgnoreCase("print")) {
            annotation.setFlag(PdfAnnotation.PRINT);
        }

        var style = getAttributeTextContent(element, "style");
        if (style != null) {
            // TODO
        }
    }

    private static float[] getFloatValues(String[] parts) {
        float[] floatValues = new float[parts.length];
        for (int j = 0; j < parts.length; j++) {
            floatValues[j] = Float.parseFloat(parts[j].trim());
        }
        return floatValues;
    }

    private static String getElementTextContent(Node element, String contentTagName) {
        var children = element.getChildNodes();
        for(int i = 0; i < children.getLength(); i++) {
            if(children.item(i).getNodeType() != Node.ELEMENT_NODE) continue; // skip text nodes
            if(children.item(i).getNodeName().equals(contentTagName)) return children.item(i).getTextContent();
        }
        return null;
    }

    private static Rectangle getRectValues(Node element) {
        var attributeContent = getAttributeTextContent(element, "rect");
        if(attributeContent == null) return null;

        var values = Arrays.stream(attributeContent.split(","))
                .map(value -> Float.parseFloat(value.trim()))
                .toList();

        return new Rectangle(values.get(0), values.get(1), values.get(2)-values.get(0), values.get(3)-values.get(1));
    }

    private static String getAttributeTextContent(Node element, String attributeName) {
        var attribute = element.getAttributes().getNamedItem(attributeName);
        if(attribute != null) return attribute.getNodeValue();
        return null;
    }

    private static Color getColor(Node element) {
        String decodedColor = getAttributeTextContent(element, "color");
        if(decodedColor != null) {
            java.awt.Color awtColor = java.awt.Color.decode(decodedColor);
            return new DeviceRgb(awtColor.getRed(), awtColor.getGreen(), awtColor.getBlue());
        }
        return null;
    }
}
