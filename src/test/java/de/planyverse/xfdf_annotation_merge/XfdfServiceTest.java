package de.planyverse.xfdf_annotation_merge;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;

@SpringBootTest
class XfdfServiceTest {

    @Test
    void mergeXfdfIntoPdf() throws IOException, ParserConfigurationException, SAXException {
        new XfdfService().mergeXfdfIntoPdf();
    }
}
