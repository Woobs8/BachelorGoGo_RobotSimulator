package bachelorgogo.com.robotsimulator;

import android.os.Environment;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

public class HttpVideoStreamingServer extends NanoHTTPD{
    private final String TAG = "VideoStreamingServer";
    private int mVideoSettings = 1;

    HttpVideoStreamingServer(int port) {
        super(port);
    }

    public void setVideoSettings(int settings) {
        mVideoSettings = settings;
    }

    @Override
    public Response serve(IHTTPSession session) {
        File f;
        Log.d(TAG,"Streaming video quality " + Integer.toString(mVideoSettings));
        if(mVideoSettings == 1) {
            f = new File(Environment.getExternalStorageDirectory()
                    + "/Movies/RoboGoGoVideo_426x240_29fps.mp4");
        } else if(mVideoSettings == 2) {
            f = new File(Environment.getExternalStorageDirectory()
                    + "/Movies/RoboGoGoVideo_640x360_29fps.mp4");
        } else if(mVideoSettings == 3) {
            f = new File(Environment.getExternalStorageDirectory()
                    + "/Movies/RoboGoGoVideo_1280x720_29fps.mp4");
        } else if(mVideoSettings == 4) {
            f = new File(Environment.getExternalStorageDirectory()
                    + "/Movies/RoboGoGoVideo_1280x720_59fps.mp4");
        } else {
            f = new File(Environment.getExternalStorageDirectory()
                    + "/Movies/RoboGoGoVideo_640x360_29fps.mp4");
        }
        String mimeType = "video/mp4";

        return serveFile(session.getUri(), session.getHeaders(), f, mimeType);
    }

    //Announce that the file server accepts partial content requests
    private Response createResponse(Response.Status status, String mimeType,
                                    InputStream message) {
        Response res = new Response(status, mimeType, message);
        res.addHeader("Accept-Ranges", "bytes");
        return res;
    }

    /**
     * Serves file from homeDir and its' subdirectories (only). Uses only URI,
     * ignores all headers and HTTP parameters.
     */
    private Response serveFile(String uri, Map<String, String> header,
                               File file, String mime) {
        Response res;
        try {
            // Calculate etag
            String etag = Integer.toHexString((file.getAbsolutePath()
                    + file.lastModified() + "" + file.length()).hashCode());

            // Support (simple) skipping:
            long startFrom = 0;
            long endAt = -1;
            String range = header.get("range");
            if (range != null) {
                if (range.startsWith("bytes=")) {
                    range = range.substring("bytes=".length());
                    int minus = range.indexOf('-');
                    try {
                        if (minus > 0) {
                            startFrom = Long.parseLong(range
                                    .substring(0, minus));
                            endAt = Long.parseLong(range.substring(minus + 1));
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }

            // Change return code and add Content-Range header when skipping is
            // requested
            long fileLen = file.length();
            if (range != null && startFrom >= 0) {
                if (startFrom >= fileLen) {
                    res = createResponse(Response.Status.RANGE_NOT_SATISFIABLE,
                            NanoHTTPD.MIME_PLAINTEXT, new ByteArrayInputStream("".getBytes(Charset.forName("UTF-8") )));
                    res.addHeader("Content-Range", "bytes 0-0/" + fileLen);
                    res.addHeader("ETag", etag);
                } else {
                    if (endAt < 0) {
                        endAt = fileLen - 1;
                    }
                    long newLen = endAt - startFrom + 1;
                    if (newLen < 0) {
                        newLen = 0;
                    }

                    final long dataLen = newLen;
                    FileInputStream fis = new FileInputStream(file) {
                        @Override
                        public int available() throws IOException {
                            return (int) dataLen;
                        }
                    };
                    fis.skip(startFrom);

                    res = createResponse(Response.Status.PARTIAL_CONTENT, mime,
                            fis);
                    res.addHeader("Content-Length", "" + dataLen);
                    res.addHeader("Content-Range", "bytes " + startFrom + "-"
                            + endAt + "/" + fileLen);
                    res.addHeader("ETag", etag);
                }
            } else {
                if (etag.equals(header.get("if-none-match")))
                    res = createResponse(Response.Status.NOT_MODIFIED, mime,new ByteArrayInputStream("".getBytes(Charset.forName("UTF-8") )));
                else {
                    res = createResponse(Response.Status.OK, mime,
                            new FileInputStream(file));
                    res.addHeader("Content-Length", "" + fileLen);
                    res.addHeader("ETag", etag);
                }
            }
        } catch (IOException ioe) {
            String msg = "FORBIDDEN: Reading file failed.";
            res = createResponse(Response.Status.FORBIDDEN,
                    NanoHTTPD.MIME_PLAINTEXT, new ByteArrayInputStream(msg.getBytes(Charset.forName("UTF-8") )));
        }

        return res;
    }
}
