/*
 *  Copyright (C) 2005 Leos Literak
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public
 *  License as published by the Free Software Foundation; either
 *  version 2 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; see the file COPYING.  If not, write to
 *  the Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 *  Boston, MA 02111-1307, USA.
 */
package cz.abclinuxu.servlets.utils;

import cz.abclinuxu.data.User;
import cz.abclinuxu.data.GenericObject;
import cz.abclinuxu.data.EditionRole;
import cz.abclinuxu.persistence.Persistence;
import cz.abclinuxu.persistence.PersistenceFactory;
import cz.abclinuxu.persistence.SQLTool;
import cz.abclinuxu.persistence.ldap.LdapUserManager;
import cz.abclinuxu.servlets.Constants;
import cz.abclinuxu.servlets.utils.template.FMTemplateSelector;
import cz.abclinuxu.utils.Misc;
import cz.abclinuxu.utils.config.Configurator;
import cz.abclinuxu.utils.config.ConfigurationManager;
import cz.abclinuxu.utils.config.Configurable;
import cz.abclinuxu.utils.config.ConfigurationException;
import cz.abclinuxu.utils.config.impl.AbcConfig;
import cz.abclinuxu.utils.freemarker.Tools;
import cz.abclinuxu.servlets.AbcAction;
import cz.abclinuxu.exceptions.InvalidInputException;
import cz.abclinuxu.security.ActionProtector;
import cz.abclinuxu.servlets.utils.url.UrlUtils;
import cz.abclinuxu.scheduler.UserSync;
import org.apache.log4j.Logger;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.FileUploadBase;
import org.dom4j.Node;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.DocumentHelper;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.prefs.Preferences;
import java.net.URL;
import java.net.MalformedURLException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

/**
 * Class to hold useful methods related to servlets
 * environment.
 */
public class ServletUtils implements Configurable {
    static Logger log = Logger.getLogger(ServletUtils.class);
    private static ThreadLocal currentURL = new ThreadLocal();

    static {
        Configurator configurator = ConfigurationManager.getConfigurator();
        configurator.configureAndRememberMe(new ServletUtils());
    }

    public static final String PREF_UPLOAD_PATH = "upload.path";
    public static final String DEFAULT_UPLOAD_PATH = "/tmp/upload";
    public static final String PREF_MAX_UPLOAD_SIZE = "max.upload.size";
    public static final int DEFAULT_MAX_UPLOAD_SIZE = 50*1024;

    /** holds username when performing login */
    public static final String PARAM_LOG_USER = "LOGIN";
    /** holds password when performing login */
    public static final String PARAM_LOG_PASSWORD = "PASSWORD";
    /** indicates, that user wishes to logout */
    public static final String PARAM_LOG_OUT = "logout";
    /** do not create any login cookie */
    public static final String PARAM_NO_COOKIE = "noCookie";
    /** do not redirect back to HTTPS */
    public static final String PARAM_USE_HTTPS = "useHttps";
    public static final String PARAM_URL = "url";

    public static final String VAR_ERROR_MESSAGE = "ERROR";

    private static DiskFileItemFactory uploadFactory;
    private static int uploadSizeLimit;
    private static LdapUserManager ldapManager = LdapUserManager.getInstance();

    /**
     * Combines request's parameters with parameters stored in special session
     * attribute and returns result as map. If parameter holds single value,
     * simple String->String mapping is created. If parameter holds multiple values,
     * String->List of Strings mapping is created. The key is always parameter's name.
     */
	public static Map putParamsToMap(HttpServletRequest request) throws InvalidInputException {
        HttpSession session = request.getSession();
        Map map = (Map) session.getAttribute(Constants.VAR_PARAMS);
        if ( map!=null )
            session.removeAttribute(Constants.VAR_PARAMS);
        else
            map = new HashMap();

        if ( ServletFileUpload.isMultipartContent(request) ) {
            ServletFileUpload uploader = new ServletFileUpload(uploadFactory);
            uploader.setSizeMax(uploadSizeLimit);
            try {
                List items = uploader.parseRequest(request);
                for ( Iterator iter = items.iterator(); iter.hasNext(); ) {
                    FileItem fileItem = (FileItem) iter.next();
					Object value;

                    if ( fileItem.isFormField() )
                        value = fileItem.getString("UTF-8");
                    else
						value = fileItem;

					Object existing = map.get(fileItem.getFieldName());
					if (existing == null)
						map.put(fileItem.getFieldName(), value);
					else if (existing instanceof List)
						((List) existing).add(value);
					else {
						List list = new ArrayList(2);
						list.add(existing);
						list.add(value);
						map.put(fileItem.getFieldName(), list);
					}
                }
            } catch (FileUploadBase.SizeLimitExceededException e) {
                throw new InvalidInputException("Zvolený soubor je příliš veliký!");
            } catch (FileUploadException e) {
                throw new InvalidInputException("Chyba při čtení dat.");
            } catch (UnsupportedEncodingException e) {
                log.fatal("End of the world - UTF is not supported!");
            }
        } else {
            Enumeration names = request.getParameterNames();
            while ( names.hasMoreElements() ) {
                String name = (String) names.nextElement();
                String[] values = request.getParameterValues(name);

                if ( values.length==1 ) {
                    String value = values[0];
                    map.put(name, value.trim());

                } else {
                    List list = new ArrayList(values.length);
                    for ( int i = 0; i<values.length; i++ ) {
                        String value = values[i].trim();
                        if ( value.length()==0 )
                            continue;
                        list.add(value);
                    }

                    if ( list.size() == 1)
                        map.put(name, list.get(0));
                    else
                        map.put(name, list);
                }
            }
        }
        return map;
    }

    /**
     * Sets messages and errors in env. If session contains them already,
     * values from session are used, otherwise they are created.
     */
    public static void handleMessages(HttpServletRequest request, Map env) {
        HttpSession session = request.getSession();

        Map errors = (Map) session.getAttribute(Constants.VAR_ERRORS);
        if ( errors!=null )
            session.removeAttribute(Constants.VAR_ERRORS);
        else
            errors = new HashMap(5);
        env.put(Constants.VAR_ERRORS,errors);

        List messages = (List) session.getAttribute(Constants.VAR_MESSAGES);
        if ( messages!=null )
            session.removeAttribute(Constants.VAR_MESSAGES);
        else
            messages = new ArrayList(2);
        env.put(Constants.VAR_MESSAGES,messages);
    }

    /**
     * Performs automatic login. If user wishes to log out, it does so. Otherwise
     * it searches special session attribute, request parameters or cookie for
     * login information in this order and tries user to log in. If it suceeds,
     * instance of User is stored in both session attribute and environment.
     */
    public static void handleLogin(HttpServletRequest request, HttpServletResponse response, Map env) throws IOException {
        HttpSession session = request.getSession();
        Map params = (Map) env.get(Constants.VAR_PARAMS);

        if ( params.get(PARAM_LOG_OUT) != null ) {
            params.remove(PARAM_LOG_OUT);
            session.removeAttribute(Constants.VAR_USER);
            session.invalidate();
            Cookie cookie = getCookie(request, Constants.VAR_USER);
            if (cookie != null)
                deleteCookie(cookie,response);
            return;
        }

        Persistence persistence = PersistenceFactory.getPersistence();
        User user = (User) session.getAttribute(Constants.VAR_USER);
        if (user != null) {
            env.put(Constants.VAR_USER, user);
            return;
        }

        String login = (String) params.get(PARAM_LOG_USER);
        if ( ! Misc.empty(login) ) {
            String password = (String) params.get(PARAM_LOG_PASSWORD);
            String noCookie = (String) params.get(PARAM_NO_COOKIE);

            if (password == null || password.length() == 0) {
                ServletUtils.addError(PARAM_LOG_PASSWORD, "Přihlášení selhalo - zadejte heslo.", env, null);
                return;
            }

            if ( ! ldapManager.login(login, password, LdapUserManager.SERVER_ABCLINUXU) ) {
                ServletUtils.addError(PARAM_LOG_PASSWORD, "Přihlášení selhalo - neplatná kombinace uživatelského jména a hesla.", env, null);
                return;
            }

            Integer id = SQLTool.getInstance().getUserByLogin(login);
            if (id != null)
                user = (User) persistence.findById(new User(id));
            else
                user = copyUserFromLdap(login);

            handleLoggedIn(user, "yes".equals(noCookie), response);
            params.put(ActionProtector.PARAM_TICKET, user.getSingleProperty(Constants.PROPERTY_TICKET));

            String useHttps = (String) params.get(PARAM_USE_HTTPS);
            if (!"yes".equals(useHttps)) {
                // redirect back to HTTP
                UrlUtils urlUtils = (UrlUtils) env.get(Constants.VAR_URL_UTILS);
                urlUtils.setEnforceHttp(true);
            } else {
                String url = (String) params.get(PARAM_URL);
                if (!Misc.empty(url)) {
                    url = url.replaceFirst("http:", "https:");
                    params.put(PARAM_URL, url);
                }
            }
        } else {
            Cookie cookie = getCookie(request, Constants.VAR_USER);
            if (cookie == null)
                return;

            LoginCookie loginCookie = new LoginCookie(cookie);
            int hash;
            try {
                user = (User) persistence.findById(new User(loginCookie.getId()));
                hash = loginCookie.getHash();
            } catch (Exception e) {
                deleteCookie(cookie, response);
                log.warn("Nalezena cookie s neznámým uživatelem!");
                addError(Constants.ERROR_GENERIC, "Nalezena cookie s neznámým uživatelem!", env, null);
                return;
            }

            if (!ldapManager.loginWithPasswordHash(login, hash, LdapUserManager.SERVER_ABCLINUXU)) {
                deleteCookie(cookie, response);
                log.warn("Nalezena cookie se špatným heslem!");
                addError(Constants.ERROR_GENERIC, "Nalezena cookie se špatným heslem!", env, null);
                return;
            }
            handleLoggedIn(user, true, null);
        }

        session.setAttribute(Constants.VAR_USER, user);
        env.put(Constants.VAR_USER, user);
    }

    /**
     * Adds cookie. This method must not be called, if header was written to response already.
     * @param cookie
     * @param response
     */
    public static void addCookie(Cookie cookie, HttpServletResponse response) {
        response.addCookie(cookie);
    }

    /**
     * Removes cookie from browser
     */
    public static void deleteCookie(Cookie cookie, HttpServletResponse response) {
        cookie.setMaxAge(0);
        cookie.setPath("/");
        response.addCookie(cookie);
    }

    /**
     * Finds cookie with given name in request.
     * return Cookie, if found, otherwise null.
     */
    public static Cookie getCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if ( cookies==null )
            return null;

        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName()))
                return cookie;
        }
        return null;
    }

    /**
     * Displays standard error page with custom message.
     * @param message message to be displayed
     * @param env environment
     * @param request http servlet request
     * @return page to be displayed
     */
    public static String showErrorPage(String message, Map env, HttpServletRequest request) {
        env.put(VAR_ERROR_MESSAGE,message);
        return FMTemplateSelector.select("ServletUtils", "errorPage", env, request);
    }

    /**
     * Adds message to <code>VAR_ERRORS</code> map.
     * <p>If session is not null, store messages into session. Handy for redirects and dispatches.
     */
    public static void addError(String key, String errorMessage, Map env, HttpSession session) {
        Map errors = (Map) env.get(Constants.VAR_ERRORS);
        String existingMessage = (String) errors.get(key);
        if (existingMessage != null)
            errorMessage = existingMessage + "<br>" + errorMessage;
        errors.put(key,errorMessage);
        if ( session!=null )
            session.setAttribute(Constants.VAR_ERRORS,errors);
    }

    /**
     * Adds message to <code>VAR_MESSAGES</code> list.
     * <p>If session is not null, store messages into session. Handy for redirects and dispatches.
     */
    public static void addMessage(String message, Map env, HttpSession session) {
        List messages = (List) env.get(Constants.VAR_MESSAGES);
        messages.add(message);
        if ( session!=null )
            session.setAttribute(Constants.VAR_MESSAGES,messages);
    }

    /**
     * Stores current URL, so every invocation in this thread can get it.
     * @param url
     */
    public static void setCurrentURL(String url) {
        currentURL.set(url);
    }

    /**
     * @return retreives currently processed URL from ThreadLocal variable.
     */
    public static String getCurrentURL() {
        return (String) currentURL.get();
    }

    /**
     * Constructs URL, as it was called.
     */
    public static String getURL(HttpServletRequest request) {
        StringBuffer url = request.getRequestURL();
        String query = request.getQueryString();
        if ( !Misc.empty(query) ) {
            url.append('?');
            url.append(query);
        }
        return url.toString();
    }

    /**
     * Tests whether the current method request is POST.
     * @param request current HTTP request
     * @return true when current request's method is POST
     */
    public static boolean isMethodPost(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod());
    }

    /**
     * @return referer for this page
     */
    public static URL getReferer(HttpServletRequest request) throws MalformedURLException {
        String referer = request.getHeader("Referer");
        if (referer == null)
            return null;
        return new URL(referer);
    }

    /**
     * Concatenates servletPath and pathInfo.
     * @param servletPath
     * @param pathInfo
     * @return combination of these two strings.
     */
    public static String combinePaths(String servletPath, String pathInfo) {
        if ( servletPath==null )
            return pathInfo;
        if ( pathInfo==null )
            return servletPath;
        return servletPath.concat(pathInfo);

    }

    /**
     * Handles situation, when user logs in. It checks his
     * setting, whether it shall create new cookie to simplify
     * next login (and how long cookie shall be valid) and
     * records time of last login.
     * @param user logged in user
     * @param cookieExists if true, it makes no sense to create cookie
     * @param response cookie will be placed here
     */
    private static void handleLoggedIn(User user, boolean cookieExists, HttpServletResponse response) {
        int limit = AbcConfig.getMaxWatchedDiscussionLimit();
        List rows = SQLTool.getInstance().getLastSeenComments(user.getId(), limit);
        Map<Integer, Integer> comments = new HashMap<Integer, Integer>(limit + 1, 1.0f);
        Object[] objects;
        for (Iterator iter = rows.iterator(); iter.hasNext();) {
            objects = (Object[]) iter.next();
            comments.put((Integer) objects[0], (Integer) objects[1]);
        }
        user.fillLastSeenComments(comments);

        if (! cookieExists) {
            int valid = 6*30*24*3600; // six months
            Node node = user.getData().selectSingleNode("/data/settings/cookie_valid");
            if (node != null)
                valid = Misc.parseInt(node.getText(), valid);
            if (valid != 0) {
                String[] attribs = new String[]{LdapUserManager.ATTRIB_PASSWORD_HASHCODE};
                Map<String, String> result = ldapManager.getUserInformation(user.getLogin(), attribs);
                String hash = result.get(LdapUserManager.ATTRIB_PASSWORD_HASHCODE);

                LoginCookie loginCookie = new LoginCookie(user.getId(), hash);
                Cookie cookie = loginCookie.getCookie();
                cookie.setMaxAge(valid);
                addCookie(cookie,response);
            }
        }
    }

    /**
     * Copies user from LDAP to persistence. Login must not exist there otherwise exception will be thrown
     * @param login login
     * @return user created in persistence
     * @throws cz.abclinuxu.exceptions.DuplicateKeyException login is already used
     */
    private static User copyUserFromLdap(String login) {
        LdapUserManager mgr = LdapUserManager.getInstance();
        Persistence persistence = PersistenceFactory.getPersistence();

        User user = new User();
        user.setLogin(login);
        user.setData("<data/>");

        Map<String, String> ldapUser = mgr.getUserInformation(login, UserSync.SF_USER_ALL_ATTRIBUTES);
        UserSync.syncUser(user, ldapUser, System.currentTimeMillis());
        String value = ldapUser.get(LdapUserManager.ATTRIB_REGISTRATION_DATE);
        if (value != null) {
            Document doc = user.getData();
            Element element = DocumentHelper.makeElement(doc, "/data/system/registration_date");
            element.setText(value);
        }
        persistence.create(user);
        return user;
    }

    /**
     * Proxy aware code, that extracts IP address of the client.
     */
    public static String getClientIPAddress(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();

        String header = request.getHeader("CLIENT-IP");
        if (header != null)
            return remoteAddress + "#" + header;

        header = request.getHeader("X_FORWARDED_FOR");
        if (header != null)
            return remoteAddress + "#" + header;

        header = request.getHeader("FORWARDED_FOR");
        if (header != null)
            return remoteAddress + "#" + header;

        header = request.getHeader("FORWARDED");
        if (header != null)
            return remoteAddress + "#" + header;

        return remoteAddress;
    }

    /**
     * todo presunout, tohle sem nepatri
     * Checks that both arguments have not identical content. If they do, error
     * message is added and false is returned. The reason is to forbid to save
     * form with no change at all (which would create dump revision and uselessly
     * notify users watching this object).
     * @param item item with user's changes
     * @param origItem original item
     * @param env environment
     * @return true if both items do not have identical content
     */
    public static boolean checkNoChange(GenericObject item, GenericObject origItem, Map env) {
        if (! item.contentEquals(origItem))
            return true;
        addError(Constants.ERROR_GENERIC, "Nenašli jsme žádnou změnu oproti minulé verzi. Opravdu chcete upravit tento objekt?", env, null);
        return false;
    }

    /**
     * Handles maintainance mode.
     * @return true if system is in maintainance and current operation must be aborted
     */
    public static boolean handleMaintainance(HttpServletRequest request, Map env) {
        if (! AbcConfig.isMaintainanceMode())
            return false;
        addError(Constants.ERROR_GENERIC, "Litujeme, ale tuto operaci nemůžeme právě provést. Systém je v režimu údržby.",
                 env, request.getSession());
        return true;
    }

    /**
     * Callback to configure this class.
     * @param prefs
     * @throws ConfigurationException
     */
    public void configure(Preferences prefs) throws ConfigurationException {
        String uploadPath = prefs.get(PREF_UPLOAD_PATH,DEFAULT_UPLOAD_PATH);
        uploadSizeLimit = prefs.getInt(PREF_MAX_UPLOAD_SIZE, DEFAULT_MAX_UPLOAD_SIZE);
        File file = new File(uploadPath);
        file.mkdirs();
        uploadFactory = new DiskFileItemFactory(1024,file);
    }

    /**
     * This class holds logic for login cookie.
     */
    private static class LoginCookie {
        private Integer id, hash;
        private String sid, shash;

        public LoginCookie(int id, int hash) {
            this.id = id;
            this.hash = hash;
        }

        public LoginCookie(int id, String shash) {
            this.id = id;
            this.shash = shash;
        }

        /**
         * Initializes cookie from cookie. (used for reverse operation)
         */
        public LoginCookie(Cookie cookie) {
            String value = cookie.getValue();
            if (value == null || value.length() < 6)
                return;

            int position = value.indexOf(':');
            if (position != -1) {
                sid = value.substring(0,position);
                shash = value.substring(position+1);
            } else {
                position = value.indexOf("%3A");
                if (position != -1) {
                    sid = value.substring(0,position);
                    shash = value.substring(position+3);
                } else
                    return;
            }
        }

        /**
         * Creates cookie from already supplied information.
         */
        public Cookie getCookie() {
            StringBuffer sb = new StringBuffer();
            if (sid != null)
                sb.append(sid);
            else
                sb.append(id);
            sb.append(":");
            if (shash != null)
                sb.append(shash);
            else
                sb.append(hash);
            Cookie cookie = new Cookie(Constants.VAR_USER, sb.toString());
            cookie.setPath("/");
            cookie.setHttpOnly(true);
            return cookie;
        }

        public int getHash() {
            if (hash != null)
                return hash;
            return Integer.parseInt(shash);
        }

        public int getId() {
            if (id != null)
                return id;
            return Integer.parseInt(sid);
        }
    }

    /**
     * Finds role in edition administration for given user. If the role has been already set (in session),
     * it will be reused, otherwise user's participation in predefined groups is searched and result is stored
     * in the session.
     * @param user existing and initialized user instance
     * @param request current http request
     * @return enum describing user's role
     */
    public static EditionRole getEditionRole(User user, HttpServletRequest request) {
        HttpSession session = request.getSession();
        EditionRole role = (EditionRole) session.getAttribute(Constants.VAR_EDITION_ROLE);
        if (role != null)
            return role;

        if (user.isMemberOf(Constants.GROUP_EDITORS_IN_CHIEF))
            role = EditionRole.EDITOR_IN_CHIEF;
        else if (user.isMemberOf(Constants.GROUP_EDITORS))
            role = EditionRole.EDITOR;
        else if (Tools.isAuthor(user.getId()))
            role = EditionRole.AUTHOR;
        else
            role = EditionRole.NONE;

        session.setAttribute(Constants.VAR_EDITION_ROLE, role);
        return role;
    }

    /**
     * Checks whether servlet path of request begins with given prefix
     * @param request Request done on server path
     * @param prefix Prefix to be beginning of path
     * @return {@code true} if constructed path matched with prefix, {@code false} otherwise
     */
    public static boolean pathBeginsWith(HttpServletRequest request, String prefix) {
    	String path = combinePaths(request.getServletPath(), request.getPathInfo());
    	return path != null && path.startsWith(prefix);
    }

    /**
     * Checks whether either action is defined or element with the same name
     * as action is present within HTTP passed parameters
     * @param params HTTP context
     * @param testAction Value of desired action or name of element which must be present
     * @return {@code true} if testAction should be triggered, {@code false} otherwise
     */
    public static boolean determineAction(Map params, String testAction) {
		String action = (String) params.get(AbcAction.PARAM_ACTION);
		return testAction.equals(action) || (Misc.empty(action) && !Misc.empty((String) params.get(testAction)));
	}
}
