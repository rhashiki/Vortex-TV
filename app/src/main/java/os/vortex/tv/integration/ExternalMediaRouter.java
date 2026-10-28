package os.vortex.tv.integration;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

/** For authorized remote playback through another installed player/browser. */
public final class ExternalMediaRouter {
    private ExternalMediaRouter() {}
    public static boolean openExternal(Context context, String uri, boolean isMedia) {
        if (!M3uPlaylistParser.isHttpUrl(uri)) return false;
        Intent intent = new Intent(Intent.ACTION_VIEW);
        if (isMedia) intent.setDataAndType(Uri.parse(uri),"video/*");
        else intent.setData(Uri.parse(uri));
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        try {context.startActivity(intent);return true;}
        catch(ActivityNotFoundException ex) {return false;}
    }
}
