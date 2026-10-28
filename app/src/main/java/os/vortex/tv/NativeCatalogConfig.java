package os.vortex.tv;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Read-only OEM firmware configuration: Vortex TV OS provisions it during
 * image assembly. There is no login, pairing screen, manual M3U or secret in APK.
 * The optional stock-phone debug APK simply skips OEM-only synchronization.
 */
public final class NativeCatalogConfig {
    private NativeCatalogConfig(){}
    private static final String OEM_PATH="/system/etc/vortex-tv/catalog-access";
    static String readFamilyAccess(Context context) {
        File f=new File(OEM_PATH);
        if(!f.isFile() || f.length()<20 || f.length()>96)return "";
        byte[] bytes=new byte[(int)f.length()];
        try(FileInputStream source=new FileInputStream(f)) {
            int count=0;
            while(count<bytes.length) {
                int n=source.read(bytes,count,bytes.length-count);
                if(n<0)return "";
                count+=n;
            }
        } catch(IOException ex) {return "";}
        String code=new String(bytes,StandardCharsets.US_ASCII).trim().toUpperCase(java.util.Locale.ROOT);
        return code.matches("[A-F0-9]{20}")?code:"";
    }
}
