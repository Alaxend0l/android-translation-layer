/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.media;

import java.util.HashMap;

/**
 * MediaFile is a file type table used by MediaScanner and apps that
 * need media metadata (e.g. mediastorecompat, which reflects on this
 * class through Class.forName("android.media.MediaFile")).
 *
 * ATL version: derived from AOSP's
 * media/java/android/media/MediaFile.java (framework-internal, kept
 * in sync by hand). Removed for ATL: the MTP format-code maps and
 * arguments (android.mtp.MtpConstants is not on ATL's classpath and
 * no MTP is used) and the WMA/WMV DecoderCapabilities gating (ATL
 * has no DecoderCapabilities; the entries are registered
 * unconditionally, which only widens the mime table).
 */
public class MediaFile {

    public static final int FILE_TYPE_MP3     = 1;
    public static final int FILE_TYPE_M4A     = 2;
    public static final int FILE_TYPE_WAV     = 3;
    public static final int FILE_TYPE_AMR     = 4;
    public static final int FILE_TYPE_AWB     = 5;
    public static final int FILE_TYPE_WMA     = 6;
    public static final int FILE_TYPE_OGG     = 7;
    public static final int FILE_TYPE_AAC     = 8;
    public static final int FILE_TYPE_MKA     = 9;
    public static final int FILE_TYPE_FLAC    = 10;
    private static final int FIRST_AUDIO_FILE_TYPE = FILE_TYPE_MP3;
    private static final int LAST_AUDIO_FILE_TYPE = FILE_TYPE_FLAC;

    // MIDI file types
    public static final int FILE_TYPE_MID     = 11;
    public static final int FILE_TYPE_SMF     = 12;
    public static final int FILE_TYPE_IMY     = 13;
    private static final int FIRST_MIDI_FILE_TYPE = FILE_TYPE_MID;
    private static final int LAST_MIDI_FILE_TYPE = FILE_TYPE_IMY;

    // Video file types
    public static final int FILE_TYPE_MP4     = 21;
    public static final int FILE_TYPE_M4V     = 22;
    public static final int FILE_TYPE_3GPP    = 23;
    public static final int FILE_TYPE_3GPP2   = 24;
    public static final int FILE_TYPE_WMV     = 25;
    public static final int FILE_TYPE_ASF     = 26;
    public static final int FILE_TYPE_MKV     = 27;
    public static final int FILE_TYPE_MP2TS   = 28;
    public static final int FILE_TYPE_AVI     = 29;
    public static final int FILE_TYPE_WEBM    = 30;
    private static final int FIRST_VIDEO_FILE_TYPE = FILE_TYPE_MP4;
    private static final int LAST_VIDEO_FILE_TYPE = FILE_TYPE_WEBM;

    // More video file types
    public static final int FILE_TYPE_MP2PS   = 200;
    private static final int FIRST_VIDEO_FILE_TYPE2 = FILE_TYPE_MP2PS;
    private static final int LAST_VIDEO_FILE_TYPE2 = FILE_TYPE_MP2PS;

    // Image file types
    public static final int FILE_TYPE_JPEG    = 31;
    public static final int FILE_TYPE_GIF     = 32;
    public static final int FILE_TYPE_PNG     = 33;
    public static final int FILE_TYPE_BMP     = 34;
    public static final int FILE_TYPE_WBMP    = 35;
    public static final int FILE_TYPE_WEBP    = 36;
    private static final int FIRST_IMAGE_FILE_TYPE = FILE_TYPE_JPEG;
    private static final int LAST_IMAGE_FILE_TYPE = FILE_TYPE_WEBP;

    // RAW image file types
    public static final int FILE_TYPE_DNG     = 300;
    public static final int FILE_TYPE_CR2     = 301;
    public static final int FILE_TYPE_NEF     = 302;
    public static final int FILE_TYPE_NRW     = 303;
    public static final int FILE_TYPE_ARW     = 304;
    public static final int FILE_TYPE_RW2     = 305;
    public static final int FILE_TYPE_ORF     = 306;
    public static final int FILE_TYPE_RAF     = 307;
    public static final int FILE_TYPE_PEF     = 308;
    public static final int FILE_TYPE_SRW     = 309;
    private static final int FIRST_RAW_IMAGE_FILE_TYPE = FILE_TYPE_DNG;
    private static final int LAST_RAW_IMAGE_FILE_TYPE = FILE_TYPE_SRW;

    // Playlist file types
    public static final int FILE_TYPE_M3U      = 41;
    public static final int FILE_TYPE_PLS      = 42;
    public static final int FILE_TYPE_WPL      = 43;
    public static final int FILE_TYPE_HTTPLIVE = 44;

    private static final int FIRST_PLAYLIST_FILE_TYPE = FILE_TYPE_M3U;
    private static final int LAST_PLAYLIST_FILE_TYPE = FILE_TYPE_HTTPLIVE;

    // Drm file types
    public static final int FILE_TYPE_FL      = 51;
    private static final int FIRST_DRM_FILE_TYPE = FILE_TYPE_FL;
    private static final int LAST_DRM_FILE_TYPE = FILE_TYPE_FL;

    // Other popular file types
    public static final int FILE_TYPE_TEXT          = 100;
    public static final int FILE_TYPE_HTML          = 101;
    public static final int FILE_TYPE_PDF           = 102;
    public static final int FILE_TYPE_XML           = 103;
    public static final int FILE_TYPE_MS_WORD       = 104;
    public static final int FILE_TYPE_MS_EXCEL      = 105;
    public static final int FILE_TYPE_MS_POWERPOINT = 106;
    public static final int FILE_TYPE_ZIP           = 107;

    public static class MediaFileType {
        public final int fileType;
        public final String mimeType;

        MediaFileType(int fileType, String mimeType) {
            this.fileType = fileType;
            this.mimeType = mimeType;
        }
    }

    private static final HashMap<String, MediaFileType> sFileTypeMap
            = new HashMap<String, MediaFileType>();
    private static final HashMap<String, Integer> sMimeTypeMap
            = new HashMap<String, Integer>();

    static void addFileType(String extension, int fileType, String mimeType) {
        sFileTypeMap.put(extension, new MediaFileType(fileType, mimeType));
        sMimeTypeMap.put(mimeType, Integer.valueOf(fileType));
    }

    static {
        addFileType("MP3", FILE_TYPE_MP3, "audio/mpeg");
        addFileType("MPGA", FILE_TYPE_MP3, "audio/mpeg");
        addFileType("M4A", FILE_TYPE_M4A, "audio/mp4");
        addFileType("WAV", FILE_TYPE_WAV, "audio/x-wav");
        addFileType("AMR", FILE_TYPE_AMR, "audio/amr");
        addFileType("AWB", FILE_TYPE_AWB, "audio/amr-wb");
        addFileType("WMA", FILE_TYPE_WMA, "audio/x-ms-wma");
        addFileType("OGG", FILE_TYPE_OGG, "audio/ogg");
        addFileType("OGG", FILE_TYPE_OGG, "application/ogg");
        addFileType("OGA", FILE_TYPE_OGG, "application/ogg");
        addFileType("AAC", FILE_TYPE_AAC, "audio/aac");
        addFileType("AAC", FILE_TYPE_AAC, "audio/aac-adts");
        addFileType("MKA", FILE_TYPE_MKA, "audio/x-matroska");

        addFileType("MID", FILE_TYPE_MID, "audio/midi");
        addFileType("MIDI", FILE_TYPE_MID, "audio/midi");
        addFileType("XMF", FILE_TYPE_MID, "audio/midi");
        addFileType("RTTTL", FILE_TYPE_MID, "audio/midi");
        addFileType("SMF", FILE_TYPE_SMF, "audio/sp-midi");
        addFileType("IMY", FILE_TYPE_IMY, "audio/imelody");
        addFileType("RTX", FILE_TYPE_MID, "audio/midi");
        addFileType("OTA", FILE_TYPE_MID, "audio/midi");
        addFileType("MXMF", FILE_TYPE_MID, "audio/midi");

        addFileType("MPEG", FILE_TYPE_MP4, "video/mpeg");
        addFileType("MPG", FILE_TYPE_MP4, "video/mpeg");
        addFileType("MP4", FILE_TYPE_MP4, "video/mp4");
        addFileType("M4V", FILE_TYPE_M4V, "video/mp4");
        addFileType("3GP", FILE_TYPE_3GPP, "video/3gpp");
        addFileType("3GPP", FILE_TYPE_3GPP, "video/3gpp");
        addFileType("3G2", FILE_TYPE_3GPP2, "video/3gpp2");
        addFileType("3GPP2", FILE_TYPE_3GPP2, "video/3gpp2");
        addFileType("MKV", FILE_TYPE_MKV, "video/x-matroska");
        addFileType("WEBM", FILE_TYPE_WEBM, "video/webm");
        addFileType("TS", FILE_TYPE_MP2TS, "video/mp2ts");
        addFileType("AVI", FILE_TYPE_AVI, "video/avi");

        addFileType("WMV", FILE_TYPE_WMV, "video/x-ms-wmv");
        addFileType("ASF", FILE_TYPE_ASF, "video/x-ms-asf");

        addFileType("JPG", FILE_TYPE_JPEG, "image/jpeg");
        addFileType("JPEG", FILE_TYPE_JPEG, "image/jpeg");
        addFileType("GIF", FILE_TYPE_GIF, "image/gif");
        addFileType("PNG", FILE_TYPE_PNG, "image/png");
        addFileType("BMP", FILE_TYPE_BMP, "image/x-ms-bmp");
        addFileType("WBMP", FILE_TYPE_WBMP, "image/vnd.wap.wbmp");
        addFileType("WEBP", FILE_TYPE_WEBP, "image/webp");

        addFileType("DNG", FILE_TYPE_DNG, "image/x-adobe-dng");
        addFileType("CR2", FILE_TYPE_CR2, "image/x-canon-cr2");
        addFileType("NEF", FILE_TYPE_NEF, "image/x-nikon-nef");
        addFileType("NRW", FILE_TYPE_NRW, "image/x-nikon-nrw");
        addFileType("ARW", FILE_TYPE_ARW, "image/x-sony-arw");
        addFileType("RW2", FILE_TYPE_RW2, "image/x-panasonic-rw2");
        addFileType("ORF", FILE_TYPE_ORF, "image/x-olympus-orf");
        addFileType("RAF", FILE_TYPE_RAF, "image/x-fuji-raf");
        addFileType("PEF", FILE_TYPE_PEF, "image/x-pentax-pef");
        addFileType("SRW", FILE_TYPE_SRW, "image/x-samsung-srw");

        addFileType("M3U", FILE_TYPE_M3U, "audio/x-mpegurl");
        addFileType("M3U", FILE_TYPE_M3U, "application/x-mpegurl");
        addFileType("PLS", FILE_TYPE_PLS, "audio/x-scpls");
        addFileType("WPL", FILE_TYPE_WPL, "application/vnd.ms-wpl");
        addFileType("M3U8", FILE_TYPE_HTTPLIVE, "application/vnd.apple.mpegurl");
        addFileType("M3U8", FILE_TYPE_HTTPLIVE, "audio/mpegurl");
        addFileType("M3U8", FILE_TYPE_HTTPLIVE, "audio/x-mpegurl");

        addFileType("FL", FILE_TYPE_FL, "application/x-android-drm-fl");

        addFileType("TXT", FILE_TYPE_TEXT, "text/plain");
        addFileType("HTM", FILE_TYPE_HTML, "text/html");
        addFileType("HTML", FILE_TYPE_HTML, "text/html");
        addFileType("PDF", FILE_TYPE_PDF, "application/pdf");
        addFileType("DOC", FILE_TYPE_MS_WORD, "application/msword");
        addFileType("XLS", FILE_TYPE_MS_EXCEL, "application/vnd.ms-excel");
        addFileType("PPT", FILE_TYPE_MS_POWERPOINT, "application/mspowerpoint");
        addFileType("FLAC", FILE_TYPE_FLAC, "audio/flac");
        addFileType("ZIP", FILE_TYPE_ZIP, "application/zip");
        addFileType("MPG", FILE_TYPE_MP2PS, "video/mp2p");
        addFileType("MPEG", FILE_TYPE_MP2PS, "video/mp2p");
    }

    public static boolean isAudioFileType(int fileType) {
        return ((fileType >= FIRST_AUDIO_FILE_TYPE &&
                fileType <= LAST_AUDIO_FILE_TYPE) ||
                (fileType >= FIRST_MIDI_FILE_TYPE &&
                fileType <= LAST_MIDI_FILE_TYPE));
    }

    public static boolean isVideoFileType(int fileType) {
        return (fileType >= FIRST_VIDEO_FILE_TYPE &&
                fileType <= LAST_VIDEO_FILE_TYPE)
            || (fileType >= FIRST_VIDEO_FILE_TYPE2 &&
                fileType <= LAST_VIDEO_FILE_TYPE2);
    }

    public static boolean isImageFileType(int fileType) {
        return (fileType >= FIRST_IMAGE_FILE_TYPE &&
                fileType <= LAST_IMAGE_FILE_TYPE)
            || (fileType >= FIRST_RAW_IMAGE_FILE_TYPE &&
                fileType <= LAST_RAW_IMAGE_FILE_TYPE);
    }

    public static boolean isPlayListFileType(int fileType) {
        return (fileType >= FIRST_PLAYLIST_FILE_TYPE &&
                fileType <= LAST_PLAYLIST_FILE_TYPE);
    }

    public static MediaFileType getFileType(String path) {
        int lastDot = path.lastIndexOf(".");
        if (lastDot < 0)
            return null;
        return sFileTypeMap.get(path.substring(lastDot + 1).toUpperCase());
    }

    public static int getFileTypeForMimeType(String mimeType) {
        Integer value = sMimeTypeMap.get(mimeType);
        return (value == null ? 0 : value.intValue());
    }

    public static String getFileExtensionForMimeType(String mimeType) {
        Integer fileType = sMimeTypeMap.get(mimeType);
        if (fileType == null)
            return null;
        for (String key : sFileTypeMap.keySet()) {
            if (sFileTypeMap.get(key).fileType == fileType.intValue())
                return key.toLowerCase(java.util.Locale.ENGLISH);
        }
        return null;
    }
}
