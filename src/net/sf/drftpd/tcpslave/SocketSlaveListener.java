/*
 * SocketSlaveListener.java
 *
 * Created on April 28, 2004, 2:03 PM
 */

package net.sf.drftpd.tcpslave;

import java.io.*;
import java.net.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Properties;
import java.util.Vector;

import net.sf.drftpd.FatalException;

import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.tcpslave.SocketSlaveImpl;

import java.net.ServerSocket;

import org.apache.oro.text.GlobCompiler;
import org.apache.oro.text.regex.MalformedPatternException;
import org.apache.oro.text.regex.Pattern;
import org.apache.oro.text.regex.Perl5Matcher;

import socks.server.Ident;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 *
 * @author  jbarrett
 */
public class SocketSlaveListener extends Thread {

    private static final Logger logger = Logger.getLogger(SocketSlaveListener.class.getName());

    private int _port;
    private ConnectionManager _conman;
    private ServerSocket sock;
    
    /** Creates a new instance of SocketSlaveListener */
    public SocketSlaveListener(ConnectionManager conman, int port) {
        _conman = conman;
        _port = port;
        start();
    }
    
    public void run() {
        try {
            sock = new ServerSocket(_port);
        } catch (Exception e) {
            throw new FatalException(e);
        }
        Socket slave;
        while (true) {
            try {
                slave = sock.accept();
            } catch (Exception e) {
                throw new FatalException(e);
            }
            Ident identObj = new Ident(slave);
            String ident;
            if (identObj.successful) {
                ident = identObj.userName;
            } else {
                ident = "";
            }
            InetAddress addr = slave.getInetAddress();
            Perl5Matcher m = new Perl5Matcher();
            
            String ipmask = ident + "@" + addr.getHostAddress();
            String hostmask = ident + "@" + addr.getHostName();
            Collection slaves = _conman.getSlaveManager().getSlaves();
            boolean match = false;
            RemoteSlave thisone = null;
            for (Iterator i=slaves.iterator(); i.hasNext();) {
                RemoteSlave rslave = (RemoteSlave)i.next();
                if (rslave.isAvailable()) continue; // already connected
                if (rslave.getConfig().get("addr") == null) continue; // not a socketslave
                if ((String)rslave.getConfig().get("addr") != "Dynamic") continue; // is a static slave
                // unconnected dynamic socket slave, test masks
                for (Iterator i2 = rslave.getMasks().iterator(); i2.hasNext(); ) {
                    String mask = (String) i2.next();
                    Pattern p;
                    try {
                        p = new GlobCompiler().compile(mask);
                    } catch (MalformedPatternException ex) {
                        throw new RuntimeException(
                        "Invalid glob pattern: " + mask,
                        ex
                        );
                    }
                    
                    // ip
                    if (m.matches(ipmask, p) || m.matches(hostmask, p)) {
                        match = true;
                        thisone = rslave;
                        break;
                    }
                } //for
                if (match) break;
            } //for
            if (!match) continue; // no matching masks
            try {
                SocketSlaveImpl tmp = new SocketSlaveImpl(_conman, thisone.getConfig(), slave);
            } catch (Exception e) {
            }
        }
    }
}
