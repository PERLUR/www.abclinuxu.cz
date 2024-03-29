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
package cz.abclinuxu.servlets.utils.url;

import cz.abclinuxu.data.Relation;
import cz.abclinuxu.data.GenericObject;
import cz.abclinuxu.data.Category;
import cz.abclinuxu.data.Item;
import cz.abclinuxu.AbcException;
import cz.abclinuxu.servlets.utils.ServletUtils;
import cz.abclinuxu.servlets.Constants;
import cz.abclinuxu.utils.freemarker.Tools;
import cz.abclinuxu.utils.config.impl.AbcConfig;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.ServletException;
import javax.servlet.RequestDispatcher;
import java.util.*;
import java.io.IOException;
import java.net.URL;

/**
 * Simple class used for generating URLs, which remembers
 * their prefix and session id.
 */
public class UrlUtils {

    public static final String PREFIX_ADMINISTRATION = "/sprava";
    public static final String PREFIX_AUTHORS = "/autori";
    public static final String PREFIX_BAZAAR = "/bazar";
    public static final String PREFIX_BLOG = "/blog";
    public static final String PREFIX_CLANKY = "/clanky"; // todo rename to PREFIX_ARTICLES
    public static final String PREFIX_DICTIONARY = "/slovnik";
    public static final String PREFIX_DRIVERS = "/ovladace";
    public static final String PREFIX_FAQ = "/faq";
    public static final String PREFIX_FORUM = "/forum";
    public static final String PREFIX_HARDWARE = "/hardware";
    public static final String PREFIX_NEWS = "/zpravicky";
    public static final String PREFIX_PEOPLE = "/lide";
    public static final String PREFIX_PERSONALITIES = "/kdo-je";
    public static final String PREFIX_POLLS = "/ankety";
    public static final String PREFIX_SCREENSHOTS = "/desktopy";
    public static final String PREFIX_SERIES = "/serialy";
    public static final String PREFIX_SOFTWARE = "/software";
    public static final String PREFIX_TAGS = "/stitky";
	public static final String PREFIX_EVENTS = "/akce";
    public static final String PREFIX_VIDEOS = "/videa";
    public static final String PREFIX_NONE = "";


    static List prefixes = null;
    static {
        prefixes = new ArrayList(20);
        prefixes.add(PREFIX_ADMINISTRATION);
        prefixes.add(PREFIX_AUTHORS);
        prefixes.add(PREFIX_BAZAAR);
        prefixes.add(PREFIX_BLOG);
        prefixes.add(PREFIX_CLANKY);
        prefixes.add(PREFIX_DICTIONARY);
        prefixes.add(PREFIX_DRIVERS);
        prefixes.add(PREFIX_FAQ);
        prefixes.add(PREFIX_FORUM);
        prefixes.add(PREFIX_HARDWARE);
        prefixes.add(PREFIX_NEWS);
        prefixes.add(PREFIX_PEOPLE);
        prefixes.add(PREFIX_PERSONALITIES);
        prefixes.add(PREFIX_POLLS);
        prefixes.add(PREFIX_SCREENSHOTS);
        prefixes.add(PREFIX_SERIES);
        prefixes.add(PREFIX_SOFTWARE);
		prefixes.add(PREFIX_EVENTS);
        prefixes.add(PREFIX_TAGS);
        prefixes.add(PREFIX_VIDEOS);
    }

    /** default prefix to URL */
    String prefix;
    HttpServletResponse response;
    HttpServletRequest request;
    boolean enforceHttp = false;

    /**
     * Creates new UrlUtils instance.
     * @param url Request URI
     */
    public UrlUtils(String url, HttpServletResponse response, HttpServletRequest request) {
        this.prefix = getPrefix(url);
        this.response = response;
        this.request = request;
    }

    /**
     * Constructs new URL, which doesn't lose context prefix and session id.
     * @param url URL to be encoded
     * @param prefix Prefix overiding default value or null
     */
    public String make(String url, String prefix) {
        String out;
        if (prefix == null)
            prefix = this.prefix;
        if (! PREFIX_NONE.equals(getPrefix(url)))
            out = url;
        else
            out = prefix+url;
        return response.encodeURL(out);
    }

    /**
     * Constructs new URL, which doesn't lose context prefix and session id.
     * @param url URL to be encoded
     */
    public String make(String url) {
        return make(url,null);
    }

    /**
     * Constructs new URL, which doesn't contain prefix and doesn't loose session id.
     */
    public String noPrefix(String url) {
        return response.encodeURL(url);
    }

    /**
     * Constructs new URL, which doesn't lose context prefix and session id. This will work
     * with response.redirect().
     * @param url URL to be encoded
     */
    public String constructRedirectURL(String url) {
        String out = url;
        //if (PREFIX_NONE.equals(getPrefix(url))) out = prefix+url;
        return response.encodeRedirectURL(completeUrl(out));
    }

    /**
     * Constructs new URL, which doesn't lose context prefix. This version is dedicated
     * to response.getDispatcher().dispatch().
     * @param url URL to be encoded
     */
    public String constructDispatchURL(String url) {
        String out = url;
        if (PREFIX_NONE.equals(getPrefix(url))) out = prefix+url;
        return out;
    }

    /**
     * @return Prefix used by this instance
     */
    public String getPrefix() {
        return prefix;
    }

    /**
     * Finds prefix used in presented url.
     */
    public static String getPrefix(String url) {
        if ( url==null || url.length()==0 ) return PREFIX_NONE;
        for (Iterator iter = prefixes.iterator(); iter.hasNext();) {
            String prefix = (String) iter.next();
            if ( url.startsWith(prefix) ) return prefix;
        }
        return PREFIX_NONE;
    }

    /**
     * Redirects to desired URL, keeping session and prefix.
     */
    public void redirect(HttpServletResponse response, String url) throws IOException {
        String url2 = constructRedirectURL(url);
        response.sendRedirect(url2);
    }

    /**
     * Adds domain, protocol and/or port.
     * @param url e.g. "/abcdef"
     * @return completed URL
     */
    public String completeUrl(String url) {
        if (url.startsWith("http:") || url.startsWith("https:"))
            return url;

        boolean secure = false;

        if (!enforceHttp) {
            try {
                URL referer = ServletUtils.getReferer(request);
                if (referer != null)
                    secure = "https".equals(referer.getProtocol());
            } catch (Exception e) {
            }
        }

        String domain = request.getServerName();
        StringBuffer composedUrl = new StringBuffer();
        int port = request.getServerPort();

        composedUrl.append(secure ? "https://" : "http://" );
        composedUrl.append(domain);

        if (/*(secure && port != 443) ||*/ (!secure && port != 80)) {
            composedUrl.append(':');
            composedUrl.append(port);
        }

        composedUrl.append(url);
        return composedUrl.toString();
    }

    /**
     * Redirects to desired URL, keeping session and optionally prefix.
     */
    public void redirect(HttpServletResponse response, String url, boolean keepPrefix) throws IOException {
        if (keepPrefix) {
            redirect(response, url);
            return;
        }
        String url2 = response.encodeRedirectURL(completeUrl(url));
        response.sendRedirect(url2);
    }

    /**
     * Dispatches to desired URL, keeping prefix.
     */
    public void dispatch(HttpServletRequest request, HttpServletResponse response, String url) throws ServletException, IOException {
        String url2 = constructDispatchURL(url);
        RequestDispatcher dispatcher = request.getRequestDispatcher(url2);
        dispatcher.forward(request,response);
    }

    /**
     * Creates url for relation. If relation url is set, it will be used, otherwise
     * it will be constructed from prefix and relation id.
     * @param relation relation for which we need url.
     * @param prefix url prefix, one of constants from this class
     * @return url to display this relation
     */
    public static String getRelationUrl(Relation relation, String prefix) {
        if (relation == null)
            throw new AbcException("Špatný vstup: relace nesmí být prázdná!");
        relation = (Relation) Tools.sync(relation);

        if (relation.getUrl() != null)
            return relation.getUrl();

        GenericObject child = relation.getChild();
        if (child instanceof Category) {
            Category category = (Category) child;
            if (category.getType() == Category.BLOG)
                return UrlUtils.PREFIX_BLOG + "/" + category.getSubType();
            else
                return prefix + "/dir/" + relation.getId();
        }

        if (! (child instanceof Item))
            return prefix + "/show/" + relation.getId();

        Item item = (Item) child;
        int type = item.getType();

        if (type == Item.BLOG || type == Item.UNPUBLISHED_BLOG)
            return Tools.getUrlForBlogStory(relation);
        else if (type == Item.DISCUSSION)
            return Tools.getUrlForDiscussion(relation);
        return prefix + "/show/" + relation.getId();
    }

    /**
     * Creates url for relation. If relation url is set, it will be used, otherwise
     * it will be constructed from prefix and relation id.
     * @param relation relation for which we need url
     * @return url to display this relation
     */
    public String getRelationUrl(Relation relation) {
        return getRelationUrl(relation, prefix);
    }

    /**
     * Returns URL to be used by search engines for given relation. All other variants (print), schemes (https)
     * or layouts (pda) shall be ignored as duplicates.
     * @param relation relation for which we need url
     * @param env
     * @return absolute url
     */
    public static String getCanonicalUrl(Relation relation, Map env) {
        UrlUtils urlUtils = (UrlUtils) env.get(Constants.VAR_URL_UTILS);
        return AbcConfig.getAbsoluteUrl() + urlUtils.getRelationUrl(relation);
    }

    /**
     * Returns URL to be used by search engines for given relation. All other variants (print), schemes (https)
     * or layouts (pda) shall be ignored as duplicates.
     * @param url local url starting with slash
     * @return absolute url
     */
    public static String getCanonicalUrl(String url) {
        return AbcConfig.getAbsoluteUrl() + url;
    }

    /**
     * Creates url for relation. If relation url is set, it will be used, otherwise
     * it will be constructed from prefix and relation id.
     * @param relation relation for which we need url
     * @return url to display this relation
     */
    public String url(Relation relation) {
        return getRelationUrl(relation, prefix);
    }

    /**
     * @return true, if HTTPS shouldn't be used
     */
    public boolean getEnforceHttp() {
        return enforceHttp;
    }

    public void setEnforceHttp(boolean enforceHttp) {
        this.enforceHttp = enforceHttp;
    }
}
