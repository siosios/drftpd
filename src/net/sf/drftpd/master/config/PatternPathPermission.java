/*
 * Created on 2003-sep-17
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.master.config;

import java.util.Collection;

import net.sf.drftpd.remotefile.LinkedRemoteFile;

import org.apache.oro.text.regex.Pattern;
import org.apache.oro.text.regex.Perl5Matcher;

/**
 * @author mog
 * @version $Id: PatternPathPermission.java,v 1.5 2003/11/17 20:13:10 mog Exp $
 */
public class PatternPathPermission extends PathPermission {
	Pattern _pat;
	public PatternPathPermission(Pattern pat, Collection users) {
		super(users);
		 _pat = pat;
	}
	
	public boolean checkPath(LinkedRemoteFile file) {
		String path = file.getPath();
		if(file.isDirectory()) path = path.concat("/");
		Perl5Matcher m = new Perl5Matcher();
		return m.matches(path, _pat); 
	}
}
