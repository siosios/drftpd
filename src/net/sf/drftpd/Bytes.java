package net.sf.drftpd;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

/**
 * See http://physics.nist.gov/cuu/Units/binary.html for an explanation of binary multiples.
 * 
 * @author mog
 * @version $Id: Bytes.java,v 1.13 2004/02/04 17:13:12 mog Exp $
 */
public class Bytes {
	private static class Multiple {
		private long _binaryMultiple;
		private long _multiple;

		private char _suffix;
		public Multiple(char suffix, long multiple, long binarymultiple) {
			_suffix = suffix;
			_multiple = multiple;
			_binaryMultiple = binarymultiple;
		}

		public long getBinaryMultiple() {
			return _binaryMultiple;
		}

		public long getMultiple() {
			return _multiple;
		}

		public char getSuffix() {
			return _suffix;
		}

	}
	private static final DecimalFormat FORMAT;
	static {
		DecimalFormatSymbols formatsymbols = new DecimalFormatSymbols();
		//formatsymbols.setDecimalSeparator('.');
		FORMAT = new DecimalFormat("0.0", formatsymbols);
		FORMAT.setDecimalSeparatorAlwaysShown(true);
	}

	public static final long GIBI = 1073741824L;
	public static final long GIGA = 1000000000L;
	public static final long KIBI = 1024L;
	public static final long KILO = 1000L;
	public static final long MEBI = 1048576L;
	public static final long MEGA = 1000000L;

	private static final Multiple[] MULTIPLES =
		new Multiple[] {
			new Multiple('E', 1000000000000000000L, 1152921504606846976L),
			new Multiple('P', 1000000000000000L, 1125899906842624L),
			new Multiple('T', 1000000000000L, 1099511627776L),
			new Multiple('G', 1000000000L, 1073741824L),
			new Multiple('M', 1000000L, 1048576L),
			new Multiple('K', 1000L, 1024L)};

	public static final long PETA = 1000000000000000L;
	public static final long TEBI = 1099511627776L;
	public static final long TERRA = 1000000000000L;

	public static String formatBytes(long bytes) {
		return formatBytes(bytes, Boolean.getBoolean(System.getProperty("bytes.binary", "false")));
	}

	public static String formatBytes(long bytes, boolean binary) {
		long absbytes = Math.abs(bytes);
		for (int i = 0; i < MULTIPLES.length; i++) {
			Multiple multiple = MULTIPLES[i];
			long multipleVal =
				binary ? multiple.getBinaryMultiple() : multiple.getMultiple();
			if (absbytes >= multipleVal) {
				return Bytes.FORMAT.format((float) bytes / multipleVal)
						+ multiple.getSuffix()
						+ (binary ? "i" : "")
						+ "B";
			}
		}
		return bytes+"B";
	}
	/**
	 * Parse a string representation of an amount of bytes. The suffix b is optional and makes no different, this method is case insensitive.
	 * <p>
	 * For example:
	 * 1000 = 1000 bytes
	 * 1000b = 1000 bytes
	 * 1000B = 1000 bytes
	 * 1k = 1000 bytes
	 * 1kb = 1000 bytes
	 * 1t = 1 terrabyte
	 * 1tib = 1 tebibyte
	 */
	public static long parseBytes(String str) throws NumberFormatException {
		str = str.toUpperCase();
		if (str.endsWith("B"))
			str = str.substring(0, str.length() - 1);

		boolean binary = false;
		if (str.endsWith("I")) {
			str = str.substring(0, str.length() - 1);
			binary = true;
		}

		char suffix = Character.toUpperCase(str.charAt(str.length() - 1));
		if (Character.isDigit(suffix)) {
			return Long.parseLong(str);
		}
		str = str.substring(0, str.length() - 1);

		for (int i = 0; i < MULTIPLES.length; i++) {
			Multiple multiple = MULTIPLES[i];
			//long multiple = ;
			if (suffix == multiple.getSuffix()) {
				return Long.parseLong(str)
					* (binary
						? multiple.getBinaryMultiple()
						: multiple.getMultiple());
			}
		}
		throw new IllegalArgumentException("Unknown suffix " + suffix);
	}
}
