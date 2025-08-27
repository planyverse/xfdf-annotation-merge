package de.planyverse.xfdf_annotation_merge;

import com.itextpdf.forms.xfdf.XfdfObject;
import com.itextpdf.forms.xfdf.XfdfObjectFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

@Service
public class XfdfService {
    public void mergeXfdfIntoPdf() throws IOException, ParserConfigurationException, SAXException {
        String pdfForm = "src/main/resources/test-document.pdf";
        String xfdf = "src/main/resources/test-document-annotations.xfdf";
        String dest = "src/main/resources/output.pdf";

        PdfDocument pdfDocument = new PdfDocument(
                new PdfReader(new FileInputStream(pdfForm)),
                new PdfWriter(new FileOutputStream(dest))
        );

        XfdfObjectFactory factory = new XfdfObjectFactory();
        XfdfObject xfdfObject = factory.createXfdfObject(new FileInputStream(xfdf));
        xfdfObject.mergeToPdf(pdfDocument, pdfForm);
        pdfDocument.close();
    }
}
