package de.planyverse.xfdf_annotation_merge;

import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.annot.*;
import com.itextpdf.kernel.pdf.colorspace.PdfColorSpace;
import com.itextpdf.kernel.pdf.xobject.PdfFormXObject;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

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
            Node element = annotations.item(i);

            if(element.getNodeType() != Node.ELEMENT_NODE) continue; // skip text nodes

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
                        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
                        PdfDictionary appearance = new PdfDictionary();
                        PdfStream stream = new PdfStream(bytes);
                        var xObject = new PdfFormXObject(stream); // just to check if the content is a valid PDF stream
                        appearance.put(PdfName.N, stream);
                        annotation = new PdfStampAnnotation(rect)
                                .setAppearance(PdfName.N, xObject.getPdfObject());
                        }
                }
                case "circle" -> {
                    DeviceRgb color = new DeviceRgb(0,0,0);
                    String decodedColor = getAttributeTextContent(element, "color");
                    if(decodedColor != null) {
                        Color awtColor = Color.decode(decodedColor);
                        color = new DeviceRgb(awtColor.getRed(), awtColor.getGreen(), awtColor.getBlue());
                    }
                    annotation = new PdfCircleAnnotation(rect)
                            .setColor(color);
                }
                case "polyline" -> {
                    var content = getElementTextContent(element, "vertices");
                    if(content != null) {
                        String[] parts = content.split("[,;]");
                        float[] floatValues = new float[parts.length]; // line1: x1,y1,x2,y2; line2: x3,y3,x4,y4; ...
                        for (int j = 0; j < parts.length; j++) {
                            floatValues[j] = Float.parseFloat(parts[j].trim());
                        }
                        for (int pos = 0; pos <= floatValues.length - 4; pos += 4) {
                            var subArray = Arrays.copyOfRange(floatValues, pos, pos + 4);
                            annotation = new PdfLineAnnotation(rect,Arrays.copyOfRange(floatValues, pos, pos+4));
                            page.addAnnotation(annotation);
                        }
                        annotation = null;
                    }
                }
            }

            if(annotation != null) page.addAnnotation(annotation);
        }
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
}
