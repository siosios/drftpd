package net.sf.drftpd.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

/**
 * @author mog
 *
 */
public class SSLGetContext {
	public static SSLContext getSSLContext()
		throws GeneralSecurityException, IOException {
		SSLContext ctx = SSLContext.getInstance("TLS");

		KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");

		KeyStore ks = KeyStore.getInstance("JKS");
		ks.load(new FileInputStream("drftpd.key"), "drftpd".toCharArray());

		kmf.init(ks, "drftpd".toCharArray());

		ctx.init(kmf.getKeyManagers(), null, null);
		return ctx;
	}
}
