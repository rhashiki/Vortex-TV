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
        public final String channelId,start,stop,title,description,displayName;
        Programme(String channelId,String start,String stop,String title,String description,String displayName) {
            this.channelId=channelId; this.displayName=displayName; this.start=start; this.stop=stop;
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
            String channelId="",start="",stop="", title="",desc="",field="",channelDefinition="";
            Map<String,String> channelNames=new HashMap<>();
            StringBuilder content=new StringBuilder();
            @Override public void startElement(String uri,String localName,String qName,Attributes a) {
                if("channel".equals(qName))channelDefinition=val(a,"id");
                if ("programme".equals(qName)) {
                    channelId=val(a,"channel"); start=val(a,"start"); stop=val(a,"stop");title="";desc="";
                }
                field=qName;content.setLength(0);
            }
            @Override public void characters(char[] ch,int start,int length) {
                if ("title".equals(field)||"desc".equals(field)||"display-name".equals(field)) content.append(ch,start,length);
            }
            @Override public void endElement(String uri,String localName,String qName) throws SAXException {
                if("display-name".equals(qName)&&!channelDefinition.isEmpty()&&!channelNames.containsKey(channelDefinition))
                    channelNames.put(channelDefinition,content.toString());
                if("channel".equals(qName))channelDefinition="";
                if ("title".equals(qName)) title=content.toString();
                if ("desc".equals(qName)) desc=content.toString();
                if ("programme".equals(qName)) {
                    String label=channelNames.get(channelId);
                    boolean accepted=allowedIds==null || allowedIds.contains(channelId);
                    String alias=label;
                    if(!accepted&&label!=null){
                        String labelKey=normalize(label);
                        for(String allowed:allowedIds){
                            if(normalize(allowed).equals(labelKey)){accepted=true;alias=allowed;break;}
                        }
                    }
                    if(accepted){
                        if (out.size()>=200000) throw new SAXException("Max EPG entries exceeded");
                        out.add(new Programme(channelId,start,stop,title,desc,alias==null?"":alias));
                    }
                }
                field=""; content.setLength(0);
            }
            private String normalize(String value){
                return java.text.Normalizer.normalize(value.toLowerCase(java.util.Locale.ROOT),
                    java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                    .replaceAll("[^a-z0-9]", "").replaceAll("(fhd|hd|sd|4k)$", "");
            }
            private String val(Attributes a,String key){String v=a.getValue(key);return v==null?"":v;}
        });
        return out;
    }
}
