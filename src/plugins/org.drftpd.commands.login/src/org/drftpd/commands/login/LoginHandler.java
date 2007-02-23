/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.drftpd.commands.login;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ResourceBundle;


import org.apache.log4j.Logger;
import org.apache.oro.text.regex.MalformedPatternException;
import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.CommandInterface;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.event.UserEvent;
import org.drftpd.master.BaseFtpConnection;
import org.drftpd.master.FtpReply;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;

/**
 * @author mog
 * @author djb61
 * @version $Id: Login.java 1621 2007-02-13 20:41:31Z djb61 $
 */
public class LoginHandler extends CommandInterface {
    private static final Logger logger = Logger.getLogger(LoginHandler.class);

    /**
     * If _idntAddress == null, IDNT hasn't been used.
     */
    protected InetAddress _idntAddress;
    protected String _idntIdent;
    private ResourceBundle _bundle;

    public void initialize(String method, String pluginName) {
    	super.initialize(method, pluginName);
    	_bundle = ResourceBundle.getBundle(this.getClass().getName());
    }

    /**
     * Syntax: IDNT ident@ip:dns
     * Returns nothing on success.
     */
    public CommandResponse doIDNT(CommandRequest request) {
    	CommandResponse response;
    	request = doPreHooks(request);
    	if(!request.isAllowed()) {
    		response = request.getDeniedResponse();
    		if (response == null) {
    			response = StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
    		}
    		doPostHooks(request, response);
    		return response;
    	}
    	BaseFtpConnection conn = request.getConnection();
        if (_idntAddress != null) {
            logger.error("Multiple IDNT commands");
            response = new CommandResponse(530, "Multiple IDNT commands");
            doPostHooks(request, response);
            return response;

        }

        if (!conn.getGlobalContext().getConfig().getBouncerIps().contains(conn.getClientAddress())) {
            logger.warn("IDNT from non-bnc");

            response = StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
        	doPostHooks(request, response);
            return response;
        }

        String arg = request.getArgument();
        int pos1 = arg.indexOf('@');

        if (pos1 == -1) {
        	response = StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
        	doPostHooks(request, response);
            return response;
        }

        int pos2 = arg.indexOf(':', pos1 + 1);

        if (pos2 == -1) {
        	response = StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
        	doPostHooks(request, response);
            return response;
        }

        try {
            _idntAddress = InetAddress.getByName(arg.substring(pos1 + 1, pos2));
            _idntIdent = arg.substring(0, pos1);
        } catch (UnknownHostException e) {
            logger.info("Invalid hostname passed to IDNT", e);

            //this will most likely cause control connection to become unsynchronized
            //but give error anyway, this error is unlikely to happen
            response = new CommandResponse(501, "IDNT FAILED: " + e.getMessage());
            doPostHooks(request, response);
            return response;
        }

        // bnc doesn't expect any reply
        doPostHooks(request, null);
        return null;
    }

    /**
     * <code>PASS &lt;SP&gt; <password> &lt;CRLF&gt;</code><br>
     *
     * The argument field is a Telnet string specifying the user's
     * password.  This command must be immediately preceded by the
     * user name command.
     */
    public CommandResponse doPASS(CommandRequest request) {
    	CommandResponse response;
    	request = doPreHooks(request);
    	if(!request.isAllowed()) {
    		response = request.getDeniedResponse();
    		if (response == null) {
    			response = StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
    		}
    		doPostHooks(request, response);
    		return response;
    	}
    	BaseFtpConnection conn = request.getConnection();
        if (conn.getUserNull() == null) {
        	response = StandardCommandManager.genericResponse("RESPONSE_503_BAD_SEQUENCE_OF_COMMANDS");
        	doPostHooks(request, response);
            return response;
        }

        // set user password and login
        String pass = request.hasArgument() ? request.getArgument() : "";

        // login failure - close connection
        if (conn.getUserNull().checkPassword(pass)) {
            conn.setAuthenticated(true);
            conn.getGlobalContext().dispatchFtpEvent(new UserEvent(
                    conn.getUserNull(), "LOGIN", System.currentTimeMillis()));

            response = new CommandResponse(230, jprintf(_bundle, "pass.success", request.getUser()));
            
            /* TODO: Come back to this later
             * 
             */
            /*try {
                Textoutput.addTextToResponse(response, "welcome");
            } catch (IOException e) {
                logger.warn("Error reading welcome", e);
            }*/

            doPostHooks(request, response);
            return response;
        }

        response = new CommandResponse(530, jprintf(_bundle, "pass.fail", request.getUser()));
        doPostHooks(request, response);
        return response;
    }

    /**
     * <code>QUIT &lt;CRLF&gt;</code><br>
     *
     * This command terminates a USER and if file transfer is not
     * in progress, the server closes the control connection.
     */
    public CommandResponse doQUIT(CommandRequest request) {
    	CommandResponse response;
    	request = doPreHooks(request);
    	if(!request.isAllowed()) {
    		response = request.getDeniedResponse();
    		if (response == null) {
    			response = StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
    		}
    		doPostHooks(request, response);
    		return response;
    	}
    	BaseFtpConnection conn = request.getConnection();
        conn.stop();

        response = new CommandResponse(221, jprintf(_bundle, "quit.success", request.getUser()));
        doPostHooks(request, response);
        return response;
    }

    /**
     * <code>USER &lt;SP&gt; &lt;username&gt; &lt;CRLF&gt;</code><br>
     *
     * The argument field is a Telnet string identifying the user.
     * The user identification is that which is required by the
     * server for access to its file system.  This command will
     * normally be the first command transmitted by the user after
     * the control connections are made.
     */
    public CommandResponse doUSER(CommandRequest request) {
    	CommandResponse response;
    	request = doPreHooks(request);
    	if(!request.isAllowed()) {
    		response = request.getDeniedResponse();
    		if (response == null) {
    			response = StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
    		}
    		doPostHooks(request, response);
    		return response;
    	}
    	BaseFtpConnection conn = request.getConnection();

        conn.setAuthenticated(false);
        conn.setUser(null);

        // argument check
        if (!request.hasArgument()) {
        	response = StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
        	doPostHooks(request, response);
            return response;
        }

        User newUser;

        try {
            newUser = conn.getGlobalContext().getUserManager().getUserByNameIncludeDeleted(request.getArgument());
        } catch (NoSuchUserException ex) {
        	response = new CommandResponse(530, ex.getMessage());
            doPostHooks(request, response);
            return response;
        } catch (UserFileException ex) {
            logger.warn("", ex);
            response = new CommandResponse(530, "IOException: " + ex.getMessage());
            doPostHooks(request, response);
            return response;
        } catch (RuntimeException ex) {
            logger.error("", ex);

            /* TODO: Come back to this later
             * 
             */
            //throw new ReplyException(ex);
            response = new CommandResponse(530, "RuntimeException: " + ex.getMessage());
            doPostHooks(request, response);
            return response;
        }

        if (newUser.isDeleted()) {
        	/* TODO Come back and fix this
        	 * 
        	 *
        	return new Reply(530,
        			(String)newUser.getKeyedMap().getObject(
        					UserManagement.REASON,
							Reply.RESPONSE_530_ACCESS_DENIED.getMessage()));*/
        }
        if(!conn.getGlobalContext().getConfig().isLoginAllowed(newUser)) {
        	response = StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
        	doPostHooks(request, response);
            return response;
        }

        try {
            if (((_idntAddress != null) &&
                    newUser.getHostMaskCollection().check(_idntIdent,
                        _idntAddress, null)) ||
                    ((_idntAddress == null) &&
                    (newUser.getHostMaskCollection().check(null,
                        conn.getClientAddress(), conn.getControlSocket())))) {
                //success
                // max_users and num_logins restriction
                FtpReply ftpResponse = GlobalContext.getConnectionManager().canLogin(conn,
                        newUser);

                if (ftpResponse != null) {
                	response = new CommandResponse(ftpResponse.getCode(), ftpResponse.getMessage());
                	doPostHooks(request, response);
                    return response;
                }

                response = new CommandResponse(331,
                        jprintf(_bundle, "user.success", request.getUser()),
                		request.getCurrentDirectory(), newUser.getName());
                doPostHooks(request, response);
                return response;
            }
        } catch (MalformedPatternException e) {
        	response = new CommandResponse(530, e.getMessage());
            doPostHooks(request, response);
            return response;
        }

        //fail
        logger.warn("Failed hostmask check");

        response = StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
    	doPostHooks(request, response);
        return response;
    }

    public String[] getFeatReplies() {
        return null;
    }

    public void unload() {
    }
}
