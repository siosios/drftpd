package net.sf.drftpd.master.usermanager;
import java.io.IOException;
import java.util.Collection;
import java.util.Hashtable;

/**
 * This is the base class of all the user manager classes.
 * If we want to add a new user manager, we have to override
 * this class.
 *
 * @author <a href="mailto:rana_b@yahoo.com">Rana Bhattacharyya</a>
 */
public abstract class UserManager {
	
	private Hashtable users = new Hashtable();

	/**
	 * Constrcutor
	 */

	public abstract User create(String username) throws UserFileException;
	/**
	 * User existance check.
	 *
	 * @param name user name
	 */
	public abstract boolean exists(String name);
	public abstract Collection getAllGroups() throws IOException;

	/**
	 * Get all user names in the system.
	 */
	public abstract Collection getAllUsers() throws IOException;
	public abstract Collection getAllUsersByGroup(String group) throws IOException;
	/**
	 * Get user by name.
	 */
	//TODO garbage collected Map of users.
	public abstract User getUserByName(String name) throws NoSuchUserException, IOException;
	public abstract void saveAll() throws UserFileException;

}
