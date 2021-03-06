package controllers;

import java.net.URLEncoder;

import models.User;
import models.User.AccountType;
import play.Logger;
import play.Play;
import play.libs.OAuth;
import play.libs.OAuth.ServiceInfo;
import play.mvc.Before;
import play.mvc.Controller;
import play.mvc.Http.Header;
import play.mvc.With;
import box.Box;
import box.BoxAccount;
import box.BoxClient;
import box.BoxClientFactory;
import box.BoxCredentials;

import com.google.appengine.api.utils.SystemProperty;
import com.google.common.base.Joiner;
import common.request.Headers;

import dropbox.Dropbox;
import dropbox.client.DropboxClient;
import dropbox.client.DropboxClientFactory;
import dropbox.gson.DbxAccount;

/**
 * Make given controller or controller methods require login.
 * Usage:
 * @With(Login.class)
 * 
 * Based on {@link Secure}.
 *
 * @author mustpax
 */
@With({ErrorReporter.class, Namespaced.class})
public class Login extends Controller {
    private static final String REDIRECT_URL = "url";

    private static class SessionKeys {
        static final String TOKEN = "token";
        static final String SECRET = "secret";
        static final String UID = "uid";
        static final String IP = "ip";
        static final String TYPE = "type";
    }
    
    @Before(priority=10)
    static void https() {
        if (request.headers.containsKey(Headers.FORWARDED_FOR)) {
            request.remoteAddress = Headers.first(request, Headers.FORWARDED_FOR);
        }

        if (request.headers.containsKey(Headers.FORWARDED_PROTO)) {
            Header h = request.headers.get(Headers.FORWARDED_PROTO);
            request.secure = "https".equals(h.value());
        }

        // Enforce https in production
        if (Play.mode.isProd() && !request.secure) {
            redirect("https://" + request.host + request.url);
        }
    }

    @Before
    static void log() {
        Joiner joiner = Joiner.on(":").useForNull("");
        Logger.info(joiner.join(request.remoteAddress,
                                request.method,
                                request.secure ? "ssl" : "http",
                                SystemProperty.applicationVersion.get(),
                                SystemProperty.environment.get(),
                                session.get(SessionKeys.UID),
                                request.url));
    }

    @Before(unless={"login", "auth", "logout", "authCallback", "alt",
                    "boxLogin", "boxAuth", "boxAuthCallback"},
            priority=1000)
    static void checkAccess() throws Throwable {
        if (!isLoggedIn()) {
            if ("GET".equals(request.method)) {
                flash.put(REDIRECT_URL, request.url);
            }

            login();
        }
    }

    public static void alt() {
        boolean alt = true;
        flash.keep(REDIRECT_URL);
        render("Login/login.html", alt);
    }
    
    public static void login() {
        if (isLoggedIn()) {
            Logger.info("User visited login url, but already logged in.");
            redirectToOriginalURL();
        } else {
            flash.keep(REDIRECT_URL);
            render();
        }
    }

    public static boolean isAdmin() {
        User u = getUser();
        return u != null && u.isAdmin();
    }

    public static boolean isLoggedIn() {
        return getUser() != null;
    }

    public static void authCallback() throws Exception {
        String token = session.get(SessionKeys.TOKEN);
        String secret = session.get(SessionKeys.SECRET);
        ServiceInfo serviceInfo = Dropbox.OAUTH;
        OAuth.Response oauthResponse = OAuth.service(serviceInfo).retrieveAccessToken(token, secret);
        if (oauthResponse.error == null) {
            Logger.info("Succesfully authenticated with Dropbox.");
            User u = upsertUser(oauthResponse.token, oauthResponse.secret);
            session.put(SessionKeys.TYPE, AccountType.DROPBOX.name());
            session.put(SessionKeys.UID, u.id);
            session.put(SessionKeys.IP, request.remoteAddress);
            session.remove(SessionKeys.TOKEN, SessionKeys.SECRET);
            redirectToOriginalURL();
        } else {
            Logger.error("Error connecting to Dropbox: " + oauthResponse.error);
            session.remove(SessionKeys.TOKEN, SessionKeys.SECRET);
            forbidden("Could not authenticate with Dropbox.");
        }
    }

    public static void auth() throws Exception {
        flash.keep(REDIRECT_URL);
        ServiceInfo serviceInfo = Dropbox.OAUTH;
        OAuth oauth = OAuth.service(serviceInfo);
        OAuth.Response oauthResponse = oauth.retrieveRequestToken();
        if (oauthResponse.error == null) {
            Logger.info("Redirecting to Dropbox for auth.");
            session.put(SessionKeys.TOKEN, oauthResponse.token);
            session.put(SessionKeys.SECRET, oauthResponse.secret);
            redirect(oauth.redirectUrl(oauthResponse.token) +
                    "&oauth_callback=" +
                    URLEncoder.encode(request.getBase() + "/auth-cb", "UTF-8"));
        } else {
            Logger.error("Error connecting to Dropbox: " + oauthResponse.error);
            error("Error connecting to Dropbox.");
        }
    }
    
    /**
     * Ensure that the Dropbox user authenticated with the given oauth credentials
     * is in the datastore.
     */
    private static User upsertUser(String token, String secret) {
        DropboxClient client = DropboxClientFactory.create(token, secret);
        DbxAccount account = client.getAccount();
        return User.upsert(account, token, secret);
    }
    
    /**
     * @return the currently logged in user, null if no logged in user
     */
    public static User getUser() {
        AccountType type = AccountType.fromDbValue(session.get(SessionKeys.TYPE));
        if (type == null) {
            Logger.info("User not logged in: no account type in session.");
            return null;
        }

        String uid = session.get(SessionKeys.UID);
        if (uid == null) {
            Logger.info("User not logged in: no uid in session.");
            return null;
        }

        String ip = session.get(SessionKeys.IP);
        if (ip == null) {
            Logger.info("Session IP address marker missing.");
            session.remove(SessionKeys.UID);
            return null;
        }

        if (! ip.equals(request.remoteAddress)) {
            Logger.warn("Session does not match expected IP. Saved: %s Actual: %s",
                        ip, request.remoteAddress);
            return null;
        }

        try {
            User user = User.findById(type, Long.valueOf(uid));

            if (user == null) {
                Logger.warn("Session has uid, but user missing from datastore. Uid: %s", uid);
            }
            return user;
        } catch (NumberFormatException e) {
            Logger.error(e, "Invalidly formatted or missing uid: %s", uid);
            return null;
        }
    }

    public static void boxLogin() {
        if (isLoggedIn()) {
            Logger.info("User visited login url, but already logged in.");
            redirectToOriginalURL();
        } else {
            flash.keep(REDIRECT_URL);
            render();
        } 
    }

    public static void boxAuth() {
        flash.keep(REDIRECT_URL);
        redirect(Box.getAuthUrl());
    }

    public static void boxAuthCallback(String code) {
        BoxCredentials cred = Box.getCred(code);
        BoxClient client = BoxClientFactory.create(cred.token);
        BoxAccount account = client.getAccount();
        User u = User.upsert(cred, account);
        session.put(SessionKeys.TYPE, AccountType.BOX.name());
        session.put(SessionKeys.UID, u.id);
        session.put(SessionKeys.IP, request.remoteAddress);
        redirectToOriginalURL();
    }

    public static void logout() {
        session.clear();
        login();
    }
    
    static void redirectToOriginalURL() {
        String url = flash.get(REDIRECT_URL);
        if(url == null) {
            Application.index();
        }
        redirect(url);
    }
}
