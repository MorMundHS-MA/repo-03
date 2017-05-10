package login.server;

import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Calendar;

public class User {
	public String email;
	private String passwordHash;
	private String currentToken;
	public String pseudonym;
	private Calendar tokenExpiration;

	private static SecureRandom srnd = new SecureRandom();

	/**
	 * The duration a token is valid in seconds.
	 */
	private static final int tokenDuration = 10 * 60; // seconds

	/**
	 * Create a new User with an imported password hash.
	 * @param email The users registration email address and login identifier.
	 * @param passwordHash The users hashed password {@see HashPassword}.
	 * @param pseudonym The users chat pseudonym.
	 * @param forcePasswordHash Forces the object to import
	 *  the given hash instead of hashing a password.
	 */
	public User(String email,String passwordHash,String pseudonym, boolean forcePasswordHash) {
		this.email = email;
		this.passwordHash = passwordHash;
		this.pseudonym = pseudonym;
		this.currentToken = null;
		this.tokenExpiration = null;
	}

	/**
	 * Creates a new User.
	 * @param email The users registration email address and login identifier.
	 * @param password The users plain-text password. This will only be stored hashed.
	 * @param pseudonym The users chat pseudonym.
	 */
	public User(String email, String password, String pseudonym) {
		this.email = email;
		this.pseudonym = pseudonym;
		this.passwordHash = HashPassword(password);
		this.currentToken = null;
		this.tokenExpiration = null;
	}

	/**
	 * Gets the users current token. Can be null or empty.
	 * @return Returns the users token.
	 */
	public String GetToken() {
		return currentToken;
	}

	public Calendar GetTokenExpireDate() {
		return (Calendar) this.tokenExpiration.clone();
	}

	/**
	 * Checks that the given plain text password is the users password.
	 * @param password The users plain text password.
	 * @return Returns true if the password was correct otherwise false.
	 */
	public boolean VerifyPassword(String password) {
		return passwordHash.equals(HashPassword(password));
	}

	/**
	 * Generates a new token for this User.
	 * @return Returns the tokens expire date.
	 */
	public void GenerateToken() {
		byte[] rng = new byte[32];
		srnd.nextBytes(rng);
		this.currentToken = Base64.getEncoder().encodeToString(rng);
		this.tokenExpiration = Calendar.getInstance();
		this.tokenExpiration.add(Calendar.SECOND, tokenDuration);
	}

	/**
	 * Checks that the given token is correct and hasn't expired.
	 * @param token The users current token.
	 * @return Returns true if token is valid and false otherwise.
	 */
	public boolean VerifyToken(String token) {
		SimpleDateFormat sdf = new SimpleDateFormat(Service.ISO8601);
		Calendar currentTime = Calendar.getInstance();
		return token.equals(this.currentToken) &&
				currentTime.before(tokenExpiration);
	}

	/**
	 * Hashes and salts a password.
	 * @param password An unhashed user password.
	 * @return Returns the hashed and salted password.
	 */
	private String HashPassword(String password) {
		// A hashing algorithm and salt has not been specified yet.
		// TODO : Hash here
		return password;
	}
}
