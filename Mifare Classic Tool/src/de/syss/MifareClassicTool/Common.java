/*
 * Copyright 2013 Gerhard Klostermeier
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */


package de.syss.MifareClassicTool;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.NfcA;
import android.os.Environment;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;

/**
 * Common functions and variables for all Activities.
 * @author Gerhard Klostermeier
 */
public class Common {

    /**
     * The directory name of the root directory of this app
     * (on external storage).
     */
    public static final String HOME_DIR = "/MifareClassicTool";
    /**
     * The directory name  of the key files directory.
     * (sub directory of {@link #HOME_DIR}.)
     */
    public static final String KEYS_DIR = "/key-files";
    /**
     * The directory name  of the dump files directory.
     * (sub directory of {@link #HOME_DIR}.)
     */
    public static final String DUMPS_DIR = "/dump-files";
    /**
     * The directory name of the folder where temporary files are
     * stored. The directory will be cleaned during the creation of
     * the main activity ({@link Activities.MainActivity}).
     * (sub directory of {@link #HOME_DIR}.)
     */
    public static final String TMP_DIR = "/tmp";
    /**
     * This file contains some standard Mifare keys.
     * <ul>
     * <li>0xFFFFFFFFFFFF - Unformatted, factory fresh tags.</li>
     * <li>0xA0A1A2A3A4A5 - First sector of the tag (Mifare MAD).</li>
     * <li>0xD3F7D3F7D3F7 - All other sectors.</li>
     * <li>Others from {@link #SOME_CLASSICAL_KNOWN_KEYS}.</li>
     * </ul>
     */
    public static final String STD_KEYS = "std.keys";

    /**
     * Some classical Mifare keys retrieved by a quick google search
     * ("mifare standard keys").
     */
    public static final String[] SOME_CLASSICAL_KNOWN_KEYS =
        {   "000000000000",
            "A0B0C0D0E0F0",
            "A1B1C1D1E1F1",
            "B0B1B2B3B4B5",
            "4D3A99C351DD",
            "1A982C7E459A",
            "AABBCCDDEEFF"  };

    /**
     * Possible operations the on a Mifare Classic Tag.
     */
    public enum Operations {
        Read, Write, Increment, DecTransRest, ReadKeyA, ReadKeyB, ReadAC,
        WriteKeyA, WriteKeyB, WriteAC
    }

    private static final String LOG_TAG = Common.class.getSimpleName();

    /**
     * The last detected tag.
     * Set by {@link #treatAsNewTag(Intent, Context)}
     */
    private static Tag mTag = null;
    /**
     * The last detected UID.
     * Set by {@link #treatAsNewTag(Intent, Context)}
     */
    private static byte[] mUID = null;
    /**
     * Just a global storage to save key maps generated by
     * {@link Activities.CreateKeyMapActivity}
     * @see Activities.CreateKeyMapActivity
     * @see MCReader#getKeyMap()
     */
    private static SparseArray<byte[][]> mKeyMap = null;

    private static NfcAdapter mNfcAdapter;

    /**
     * Checks if external storage is available for read and write.
     * If not, show an error Toast.
     * @param context The Context in which the Toast will be shown.
     * @return True if external storage is writable. False otherwise.
     */
    public static boolean isExternalStorageWritableErrorToast(
            Context context) {
        if (Environment.MEDIA_MOUNTED.equals(
                Environment.getExternalStorageState())) {
            return true;
        }
        Toast.makeText(context, R.string.info_no_external_storage,
                Toast.LENGTH_LONG).show();
        return false;
    }

    /**
     * Read a file line by line. The file should be a simple text file.
     * Empty lines and lines STARTING with "#" will not be interpreted.
     * @param file The file to read.
     * @param readComments Whether to read comments or to ignore them.
     * Comments are lines STARTING with "#" (and empty lines).
     * @return Array of strings representing the lines of the file.
     * If the file is empty or an error occurs "null" will be returned.
     */
    public static String[] readFileLineByLine(File file, boolean readComments) {
        BufferedReader br = null;
        String[] ret = null;
        if (file != null && file.exists()) {
            try {
                br = new BufferedReader(new FileReader(file));

                String line;
                ArrayList<String> linesArray = new ArrayList<String>();
                while ((line = br.readLine()) != null)   {
                    // Ignore empty lines.
                    // Ignore comments if readComments == false.
                    if ( !line.equals("")
                            && (readComments || !line.startsWith("#"))) {
                        linesArray.add(line);
                    }
                }
                if (linesArray.size() > 0) {
                    ret = linesArray.toArray(new String[linesArray.size()]);
                } else {
                    ret = new String[] {""};
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "Error while reading from file "
                        + file.getPath() + "." ,e);
                ret = null;
            } finally {
                if (br != null) {
                    try {
                        br.close();
                    }
                    catch (IOException e) {
                        Log.e(LOG_TAG, "Error while closing file.", e);
                        ret = null;
                    }
                }
            }
        }
        return ret;
    }

    /**
     * Write an array of strings (each field is one line) to a given file.
     * If the file already exists, it will be overwritten.
     * @param file The file to write to.
     * @param lines The lines to save.
     * @return True if file writing was successful. False otherwise.
     */
    public static boolean saveFile(File file, String[] lines) {
        boolean noError = true;
        if (file != null) {
            BufferedWriter bw = null;
            try {
                bw = new BufferedWriter(new FileWriter(file));
                int i;
                for(i = 0; i < lines.length-1; i++){
                    bw.write(lines[i]);
                    bw.newLine();
               }
               bw.write(lines[i]);
            } catch (IOException e) {
                Log.e(LOG_TAG, "Error while writing to '"
                        + file.getName() + "' file.", e);
                noError = false;

            } finally {
                if (bw != null) {
                    try {
                        bw.close();
                    } catch (IOException e) {
                        Log.e(LOG_TAG, "Error while closing file.", e);
                        noError = false;
                    }
                }
            }
        } else {
            noError = false;
        }
        return noError;
    }

    /**
     * Enables the NFC foreground dispatch system for the given Activity.
     * @param targetActivity The Activity that is in foreground and wants to
     * have NFC Intents.
     * @see #disableNfcForegroundDispatch(Activity)
     */
    public static void enableNfcForegroundDispatch(Activity targetActivity) {
        if (mNfcAdapter != null && mNfcAdapter.isEnabled()) {

            Intent intent = new Intent(targetActivity,
                    targetActivity.getClass()).addFlags(
                            Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    targetActivity, 0, intent, 0);
            mNfcAdapter.enableForegroundDispatch(
                    targetActivity, pendingIntent, null, new String[][] {
                            new String[] { NfcA.class.getName() } });
        }
    }

    /**
     * Disable the NFC foreground dispatch system for the given Activity.
     * @param targetActivity An Activity that is in foreground and has
     * NFC foreground dispatch system enabled.
     * @see #enableNfcForegroundDispatch(Activity)
     */
    public static void disableNfcForegroundDispatch(Activity targetActivity) {
        if (mNfcAdapter != null && mNfcAdapter.isEnabled()) {
            mNfcAdapter.disableForegroundDispatch(targetActivity);
        }
    }

    /**
     * For Activities which want to treat new Intents as Intents with a new
     * Tag attached. If the given Intent has a Tag extra, the
     * {@link #mTag} and {@link #mUID} will be updated and a Toast
     * message will be shown in the calling Context (Activity).
     * This method will also check if the device/tag supports Mifare Classic
     * (see return values).
     * @param intent The Intent which should be checked for a new Tag.
     * @param context The Context in which the Toast will be shown.
     * @return
     * <ul>
     * <li>1 - The device/tag supports Mifare Classic</li>
     * <li>0 - The device/tag does not support Mifare Classic</li>
     * <li>-1 - Wrong Intent (action is not "ACTION_TECH_DISCOVERED").</li>
     * </ul>
     * @see #mTag
     * @see #mUID
     */
    public static int treatAsNewTag(Intent intent, Context context) {
        // Check if Intent has a NFC Tag.
        if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(intent.getAction())) {
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            mTag = tag;
            mUID = tag.getId();

            // Show Toast message with UID.
            String id = context.getResources().getString(
                    R.string.info_new_tag_found) + " (UID: ";
            id += byte2HexString(tag.getId());
            id += ")";
            Toast.makeText(context, id, Toast.LENGTH_LONG).show();

            // Return "1" if device supports Mifare Classic. "0" otherwise.
            return (Arrays.asList(tag.getTechList()).contains(
                    MifareClassic.class.getName())) ? 1 : 0;
        }
        return -1;
    }

    /**
     * Create a connected {@link MCReader} if there is a present Mifare Classic
     * tag. If there is no Mifare Classic tag a error
     * message will be displayed to toe user.
     * @param context The Context in which the error Toast will be shown.
     * @return A connected {@link MCReader} or "null" if no tag was present.
     */
    public static MCReader checkForTagAndCreateReader(Context context) {
        // Check for tag.
        if (mTag == null) {
            // Error. There is no tag.
            Toast.makeText(context, R.string.info_no_tag_found,
                    Toast.LENGTH_LONG).show();
            return null;
        }
        MCReader reader = MCReader.get(mTag);
        if (reader == null) {
         // Error. The tag is not Mifare Classic.
            Toast.makeText(context, R.string.info_no_tag_found,
                    Toast.LENGTH_LONG).show();
            return null;
        }

        boolean tagLost = false;
        try {
            reader.connect();
        } catch (Exception e) {
            tagLost = true;
        }
        if (tagLost || !reader.isConnected()) {
            // Error. The tag is gone.
            Toast.makeText(context, R.string.info_no_tag_found,
                    Toast.LENGTH_LONG).show();
            reader.close();
            return null;
        }
        return reader;
    }

    /**
     * Depending on the provided Access Conditions this method will return
     * with which key you can achieve the operation ({@link Operations})
     * you asked for.<br />
     * This method contains the table from the NXP Mifare Classic Datasheet.
     * @param c1 Access Condition byte "C!".
     * @param c2 Access Condition byte "C2".
     * @param c3 Access Condition byte "C3".
     * @param op The operation you want to do.
     * @param isSectorTrailer True if it is a Sector Trailer, False otherwise.
     * @param isKeyBReadable True if key B is readable, False otherwise.
     * @return The operation "op" is possible with:<br />
     * <ul>
     * <li>0 - Never.</li>
     * <li>1 - Key A.</li>
     * <li>2 - Key B.</li>
     * <li>3 - Key A or B.</li>
     * <li>-1 - Error.</li>
     * </ul>
     */
    public static int getOperationInfoForBlock(byte c1, byte c2, byte c3,
            Operations op, boolean isSectorTrailer, boolean isKeyBReadable) {
        // Is Sector Trailer?
        if (isSectorTrailer) {
            // Sector Trailer.
            if (op != Operations.ReadKeyA && op != Operations.ReadKeyB
                    && op != Operations.ReadAC
                    && op != Operations.WriteKeyA
                    && op != Operations.WriteKeyB
                    && op != Operations.WriteAC) {
                // Error. Sector Trailer but no Sector Trailer permissions.
                return 4;
            }
            if          (c1 == 0 && c2 == 0 && c3 == 0) {
                if (op == Operations.WriteKeyA
                        || op == Operations.WriteKeyB
                        || op == Operations.ReadKeyB
                        || op == Operations.ReadAC) {
                    return 1;
                }
                return 0;
            } else if   (c1 == 0 && c2 == 1 && c3 == 0) {
                if (op == Operations.ReadKeyB
                        || op == Operations.ReadAC) {
                    return 1;
                }
                return 0;
            } else if   (c1 == 1 && c2 == 0 && c3 == 0) {
                if (op == Operations.WriteKeyA
                        || op == Operations.WriteKeyB) {
                    return 2;
                }
                if (op == Operations.ReadAC) {
                    return 3;
                }
                return 0;
            } else if   (c1 == 1 && c2 == 1 && c3 == 0) {
                if (op == Operations.ReadAC) {
                    return 3;
                }
                return 0;
            } else if   (c1 == 0 && c2 == 0 && c3 == 1) {
                if (op == Operations.ReadKeyA) {
                    return 0;
                }
                return 1;
            } else if   (c1 == 0 && c2 == 1 && c3 == 1) {
                if (op == Operations.ReadAC) {
                    return 3;
                }
                if (op == Operations.ReadKeyA
                        || op == Operations.ReadKeyB) {
                    return 0;
                }
                return 2;
            } else if   (c1 == 1 && c2 == 0 && c3 == 1) {
                if (op == Operations.ReadAC) {
                    return 3;
                }
                if (op == Operations.WriteAC) {
                    return 2;
                }
                return 0;
            } else if   (c1 == 1 && c2 == 1 && c3 == 1) {
                if (op == Operations.ReadAC) {
                    return 3;
                }
                return 0;
            } else {
                return -1;
            }
        } else {
            // Data Block.
            if (op != Operations.Read && op != Operations.Write
                    && op != Operations.Increment
                    && op != Operations.DecTransRest) {
                // Error. Data block but no data block permissions.
                return -1;
            }
            if          (c1 == 0 && c2 == 0 && c3 == 0) {
                return (isKeyBReadable) ? 1 : 3;
            } else if   (c1 == 0 && c2 == 1 && c3 == 0) {
                if (op == Operations.Read) {
                    return (isKeyBReadable) ? 1 : 3;
                }
                return 0;
            } else if   (c1 == 1 && c2 == 0 && c3 == 0) {
                if (op == Operations.Read) {
                    return (isKeyBReadable) ? 1 : 3;
                }
                if (op == Operations.Write) {
                    return 2;
                }
                return 0;
            } else if   (c1 == 1 && c2 == 1 && c3 == 0) {
                if (op == Operations.Read
                        || op == Operations.DecTransRest) {
                    return (isKeyBReadable) ? 1 : 3;
                }
                return 2;
            } else if   (c1 == 0 && c2 == 0 && c3 == 1) {
                if (op == Operations.Read
                        || op == Operations.DecTransRest) {
                    return (isKeyBReadable) ? 1 : 3;
                }
                return 0;
            } else if   (c1 == 0 && c2 == 1 && c3 == 1) {
                if (op == Operations.Read || op == Operations.Write) {
                    return 2;
                }
                return 0;
            } else if   (c1 == 1 && c2 == 0 && c3 == 1) {
                if (op == Operations.Read) {
                    return 2;
                }
                return 0;
            } else if   (c1 == 1 && c2 == 1 && c3 == 1) {
                return 0;
            } else {
                // Error.
                return -1;
            }
        }
    }

    /**
     * Check if key B is readable.
     * Key B is readable for the following configurations:
     * <ul>
     * <li>C1 = 0, C2 = 0, C3 = 0</li>
     * <li>C1 = 0, C2 = 0, C3 = 1</li>
     * <li>C1 = 0, C2 = 1, C3 = 0</li>
     * </ul>
     * @param c1 Access Condition byte "C1"
     * @param c2 Access Condition byte "C2"
     * @param c3 Access Condition byte "C3"
     * @return True if key B is readable. False otherwise.
     */
    public static boolean isKeyBReadable(byte c1, byte c2, byte c3) {
        if (c1 == 0
                && (c2 == 0 && c3 == 0)
                || (c2 == 1 && c3 == 0)
                || (c2 == 0 && c3 == 1)) {
            return true;
        }
        return false;
    }

    /**
     * Convert the Access Condition bytes to a matrix containing the
     * resolved C1, C2 and C3 for each block.
     * @param ac The Access Conditions.
     * @return Matrix of access conditions bits (C1-C3) where the first
     * dimension is the "C" parameter (C1-C3, Index 0-2) and the second
     * dimension is the block number (Index 0-3). If the ACs are incorrect
     * null will be returned.
     */
    public static byte[][] acToACMatrix(byte ac[]) {
        // ACs correct?
        // C1 (Byte 7, 4-7) == ~C1 (Byte 6, 0-3) and
        // C2 (Byte 8, 0-3) == ~C2 (Byte 6, 4-7) and
        // C3 (Byte 8, 4-7) == ~C3 (Byte 7, 0-3)
        byte[][] acMatrix = new byte[3][4];
        if (ac.length > 2 &&
                (byte)((ac[1]>>>4)&0x0F)  == (byte)((ac[0]^0xFF)&0x0F) &&
                (byte)(ac[2]&0x0F) == (byte)(((ac[0]^0xFF)>>>4)&0x0F) &&
                (byte)((ac[2]>>>4)&0x0F)  == (byte)((ac[1]^0xFF)&0x0F)) {
            // C1, Block 0-3
            for (int i = 0; i < 4; i++) {
                acMatrix[0][i] = (byte)((ac[1]>>>4+i)&0x01);
            }
            // C2, Block 0-3
            for (int i = 0; i < 4; i++) {
                acMatrix[1][i] = (byte)((ac[2]>>>i)&0x01);
            }
            // C3, Block 0-3
            for (int i = 0; i < 4; i++) {
                acMatrix[2][i] = (byte)((ac[2]>>>4+i)&0x01);
            }
            return acMatrix;
        }
        return null;
    }

    /**
     * Check if a (hex) string is pure hex (0-9, A-F, a-f) and 16 byte
     * (32 chars) long. If not show an error Toast in the context.
     * @param hexString The string to check.
     * @param context The Context in which the Toast will be shown.
     * @return True if sting is hex an 16 Bytes long, False otherwise.
     */
    public static boolean isHexAnd16Byte(String hexString, Context context) {
        if (hexString.matches("[0-9A-Fa-f]+") == false) {
            // Error, not hex.
            Toast.makeText(context, R.string.info_not_hex_data,
                    Toast.LENGTH_LONG).show();
            return false;
        }
        if (hexString.length() != 32) {
            // Error, not 16 byte (32 chars).
            Toast.makeText(context, R.string.info_not_16_byte,
                    Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    /**
     * Check if the given block (hex string) is a value block.
     * NXP has PDFs describing what value blocks are. Google something
     * like "nxp mifare classic value block" if you want to have a
     * closer look.
     * @param hexString Block data as hex string.
     * @return True if it is a value block. False otherwise.
     */
    public static boolean isValueBlock(String hexString) {
        byte[] b = Common.hexStringToByteArray(hexString);
        if (b.length == 16) {
            // Google some NXP info PDFs about Mifare Classic to see how
            // Value Blocks are formated.
            // For better reading (~ = invert operator):
            // if (b0=b8 and b0=~b4) and (b1=b9 and b9=~b5) ...
            // ... and (b12=b14 and b13=b15 and b12=~b13) then
            if (    (b[0] == b[8] && (byte)(b[0]^0xFF) == b[4]) &&
                    (b[1] == b[9] && (byte)(b[1]^0xFF) == b[5]) &&
                    (b[2] == b[10] && (byte)(b[2]^0xFF) == b[6]) &&
                    (b[3] == b[11] && (byte)(b[3]^0xFF) == b[7]) &&
                    (b[12] == b[14] && b[13] == b[15] &&
                    (byte)(b[12]^0xFF) == b[13])) {
                return true;
            }
        }
        return false;
    }

    /**
     * Reverse a byte Array (e.g. Little Endian -> Big Endian).
     * Hmpf! Java has no Array.reverse(). And I don't want to use
     * Commons.Lang (ArrayUtils) form Apache....
     * @param array The array to reverse (in-place).
     */
    public static void reverseByteArrasInPlace(byte[] array) {
        for(int i = 0; i < array.length / 2; i++) {
            byte temp = array[i];
            array[i] = array[array.length - i - 1];
            array[array.length - i - 1] = temp;
        }
    }

    /**
     * Convert an array of bytes into a string of hex values.
     * @param bytes Bytes to convert.
     * @return The bytes in hex string format.
     */
    public static String byte2HexString(byte[] bytes) {
        String ret = "";
        if (bytes != null) {
            for (Byte b : bytes) {
                ret += String.format("%02X", b.intValue() & 0xFF);
            }
        }
        return ret;
    }

    /**
     * Convert a string of hex data into a byte array.
     * Original author is: Dave L. (http://stackoverflow.com/a/140861).
     * @param s The hex string to convert
     * @return An array of bytes with the values of the string.
     */
    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        try {
            for (int i = 0; i < len; i += 2) {
                data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                     + Character.digit(s.charAt(i+1), 16));
            }
        } catch (Exception e) {
            Log.d(LOG_TAG, "Argument(s) for hexStringToByteArray(String s)"
                    + "was not a hex string");
        }
        return data;
    }

    /**
     * Create a colored string.
     * @param data The text to be colored.
     * @param color The color for the text.
     * @return A colored string.
     */
    public static SpannableString colorString(String data, int color) {
        SpannableString ret = new SpannableString(data);
        ret.setSpan(new ForegroundColorSpan(color),
                0, data.length(), 0);
        return ret;
    }

    /**
     * Get the current active (last detected) Tag.
     * @return The current active Tag.
     * @see #mTag
     */
    public static Tag getTag() {
        return mTag;
    }

    /**
     * Set the new active Tag.
     * @param tag The new Tag.
     */
    public static void setTag(Tag tag) {
        mTag = tag;
    }

    /**
     * Get the App wide used NFC adapter.
     * @return NFC adapter.
     */
    public static NfcAdapter getNfcAdapter() {
        return mNfcAdapter;
    }

    /**
     * Set the App wide used NFC adapter.
     * @param nfcAdapter The NFC adapter that should be used.
     */
    public static void setNfcAdapter(NfcAdapter nfcAdapter) {
        mNfcAdapter = nfcAdapter;
    }

    /**
     * Get the key map generated by {@link Activities.CreateKeyMapActivity}.
     * @return A key map (see {@link MCReader#getKeyMap()}).
     */
    public static SparseArray<byte[][]> getKeyMap() {
        return mKeyMap;
    }

    /**
     * Set the key map.
     * @param value A key map (see {@link MCReader#getKeyMap()}).
     */
    public static void setKeyMap(SparseArray<byte[][]> value) {
        mKeyMap = value;
    }

    /**
     * Get the UID of the current tag.
     * @return The UID of the current tag.
     * @see #mUID
     */
    public static byte[] getUID() {
        return mUID;
    }
}
