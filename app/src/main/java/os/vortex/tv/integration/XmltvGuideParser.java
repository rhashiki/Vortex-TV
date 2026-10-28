package os.vortex.tv.integration;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/** Parses XMLTV WITHOUT external entities/DTD. Only local or explicitly authorized sources. */
public final class XmltvGuideParser {
    public static final class Programme {
        public final String channelId,start,stop,title,description;
        Programme(String channelId,String start,String stop,String title,String description) {
            this.channelId=channelId; this.start=start; this.stop=stop;
            this.title=title; this.description=description;
        }
    }
    public List<Programme> parse(Reader reader,final java.util.Set<String> allowedIds) throws Exception {
        final List<Programme> out = new ArrayList<Programme>();
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(false);
        // Fail closed if features unavailable; do not parse untrusted DTD content.
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.newSAXParser().parse(new InputSource(reader),new DefaultHandler() {
            String channelId="",start="",stop="", title="",desc="",field="";
            StringBuilder content=new StringBuilder();
            @Override public void startElement(String uri,String localName,String qName,Attributes a) {
                if ("programme".equals(qName)) {
                    channelId=val(a,"channel"); start=val(a,"start"); stop=val(a,"stop");title="";desc="";
                }
                field=qName;content.setLength(0);
            }
            @Override public void characters(char[] ch,int start,int length) {
                if ("title".equals(field)||"desc".equals(field)) content.append(ch,start,length);
            }
            @Override public void endElement(String uri,String localName,String qName) throws SAXException {
                if ("title".equals(qName)) title=content.toString();
                if ("desc".equals(qName)) desc=content.toString();
                if ("programme".equals(qName) && (allowedIds==null || allowedIds.contains(channelId))) {
                    if (out.size()>=200000) throw new SAXException("Max EPG entries exceeded");
                    out.add(new Programme(channelId,start,stop,title,desc));
                }
                field=""; content.setLength(0);
            }
            private String val(Attributes a,String key){String v=a.getValue(key);return v==null?"":v;}
        });
        return out;
    }
}
