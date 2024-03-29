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
package cz.abclinuxu.servlets.html.edit;

import cz.abclinuxu.servlets.Constants;
import cz.abclinuxu.servlets.html.view.SendEmail;
import cz.abclinuxu.servlets.utils.template.FMTemplateSelector;
import cz.abclinuxu.servlets.utils.url.UrlUtils;
import cz.abclinuxu.servlets.utils.ServletUtils;
import cz.abclinuxu.servlets.utils.url.URLManager;
import cz.abclinuxu.persistence.Persistence;
import cz.abclinuxu.persistence.PersistenceFactory;
import cz.abclinuxu.data.User;
import cz.abclinuxu.data.Relation;
import cz.abclinuxu.data.Item;
import cz.abclinuxu.data.Category;
import cz.abclinuxu.data.view.NewsCategories;
import cz.abclinuxu.security.Roles;
import cz.abclinuxu.security.AdminLogger;
import cz.abclinuxu.utils.Misc;
import cz.abclinuxu.utils.TagTool;
import cz.abclinuxu.utils.feeds.FeedGenerator;
import cz.abclinuxu.utils.freemarker.Tools;
import cz.abclinuxu.utils.parser.clean.Rules;
import cz.abclinuxu.utils.email.EmailSender;
import cz.abclinuxu.scheduler.VariableFetcher;

import cz.abclinuxu.security.ActionCheck;
import cz.abclinuxu.servlets.AbcAutoAction;
import cz.abclinuxu.servlets.utils.ParameterChecker;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.Date;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Document;

/**
 * This servlet manipulates with News.
 */
public class EditNews extends AbcAutoAction {
    static org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(EditNews.class);

    public static final String VAR_CATEGORIES = "CATEGORIES";
    public static final String VAR_CATEGORY = "CATEGORY";
    public static final String VAR_MESSAGE = "MESSAGE";
    public static final String VAR_AUTHOR = "AUTHOR";
    public static final String VAR_ADMIN = "ADMIN";
    public static final String VAR_WAITING_NEWS = "WAITING_NEWS";

    public static final String PARAM_CATEGORY = "category";
    public static final String PARAM_CONTENT = "content";
    public static final String PARAM_APPROVE = "approve";
    public static final String PARAM_MESSAGE = "message";
    public static final String PARAM_PREVIEW = "preview";
    public static final String PARAM_PUBLISH_DATE = "publish";
    public static final String PARAM_TITLE = "title";
    public static final String PARAM_FORBID_DISCUSSIONS = "forbidDiscussions";
    public static final String PARAM_UID = "uid";
    
    @ActionCheck(userRequired = true)
    public String actionAdd() throws Exception {
        env.put(VAR_CATEGORIES, NewsCategories.getAllCategories());

        Category catWaitingNews = new Category(Constants.CAT_NEWS_POOL);
        Tools.sync(catWaitingNews);

        List news = catWaitingNews.getChildren();
        env.put(VAR_WAITING_NEWS, news);

        return FMTemplateSelector.select("EditNews", "add", env, request);
    }

    @ActionCheck(userRequired = true, checkReferer = true, checkPost = true)
    public String actionAdd2() throws Exception {
        Persistence persistence = PersistenceFactory.getPersistence();
        ParameterChecker ch = new ParameterChecker(env);

        Item item = new Item(0, Item.NEWS);
        item.setData(DocumentHelper.createDocument());
        item.setOwner(user.getId());

        item.setTitle(ch.getString(PARAM_TITLE));
        setContent(item, ch);
        setCategory(item, ch);

        if (user.hasRole(Roles.NEWS_ADMIN)) {
            setOwner(item, ch);
            setForbidDiscussions(item, ch);

            Date date = ch.getDate(PARAM_PUBLISH_DATE, true);
            if (date != null)
                item.setCreated(date);
        }

        if (ch.isFailed() || params.get(PARAM_PREVIEW) != null) {
            Relation emptyRelation = new Relation(null, item, 0);
            item.setInitialized(true);
            item.setCreated(new Date());
            env.put(VAR_RELATION, emptyRelation);
            return actionAdd();
        }

        persistence.create(item);

        Relation newRelation = new Relation(new Category(Constants.CAT_NEWS_POOL),item,Constants.REL_NEWS_POOL);
        persistence.create(newRelation);
        newRelation.getParent().addChildRelation(newRelation);

        if (!user.hasRole(Roles.NEWS_ADMIN) || params.get(PARAM_FORBID_DISCUSSIONS) == null)
            EditDiscussion.createEmptyDiscussion(newRelation, user, persistence);

        UrlUtils urlUtils = (UrlUtils) env.get(Constants.VAR_URL_UTILS);
        urlUtils.redirect(response, UrlUtils.PREFIX_NEWS + "/show/"+newRelation.getId());

        return null;
    }

    /**
     * Adds admini mailing list to session and redirects to send email screen.
     */

    @ActionCheck(permittedRoles = {Roles.NEWS_ADMIN}, relationRequired = true)
    public String actionMail() throws Exception {
        Persistence persistence = PersistenceFactory.getPersistence();
        HttpSession session = request.getSession();
        Item item = (Item) relation.getChild();
        User author = (User) persistence.findById(new User(item.getOwner()));

        session.setAttribute(SendEmail.PREFIX+EmailSender.KEY_TO, author.getEmail());
        session.setAttribute(SendEmail.PREFIX+SendEmail.PARAM_DISABLE_CODE, Boolean.TRUE);
        session.setAttribute(SendEmail.PREFIX+EmailSender.KEY_SUBJECT, item.getTitle());
        session.setAttribute(SendEmail.PREFIX+EmailSender.KEY_BODY, "http://www.abclinuxu.cz/zpravicky/edit?action=edit&rid="+relation.getId());
        session.setAttribute(SendEmail.PREFIX+EmailSender.KEY_BCC, "admini@abclinuxu.cz"); // inform group of admins too

        String url = response.encodeRedirectURL("/Mail?url=/zpravicky/dir/37672");
        response.sendRedirect(url);
        return null;
    }

    @ActionCheck(itemOwnerOrRole = Roles.NEWS_ADMIN, itemType = Item.NEWS)
    public String actionEdit() throws Exception {
        Item item = (Item) relation.getChild();

        if (!user.hasRole(Roles.NEWS_ADMIN) && relation.getUpper() != Constants.REL_NEWS_POOL)
            return returnForbidden();

        params.put(PARAM_TITLE, item.getTitle());
        Element element = (Element) item.getData().selectSingleNode("/data/content");
        params.put(PARAM_CONTENT,element.getText());
        params.put(PARAM_CATEGORY,item.getSubType());
        params.put(PARAM_UID,item.getOwner());

        element = (Element) item.getData().selectSingleNode("/data/forbid_discussions");
        if (element != null)
            params.put(PARAM_FORBID_DISCUSSIONS, element.getText());

        env.put(VAR_CATEGORIES, NewsCategories.getAllCategories());

        return FMTemplateSelector.select("EditNews", "edit", env, request);
    }

    @ActionCheck(itemOwnerOrRole = Roles.NEWS_ADMIN, checkReferer = true, checkPost = true, itemType = Item.NEWS)
    public String actionEdit2() throws Exception {
        Persistence persistence = PersistenceFactory.getPersistence();
        Item item = (Item) relation.getChild().clone();
        ParameterChecker ch = new ParameterChecker(env);

        if (!user.hasRole(Roles.NEWS_ADMIN) && relation.getUpper() != Constants.REL_NEWS_POOL)
            return returnForbidden();

        item.setTitle(ch.getString(PARAM_TITLE));
        setContent(item, ch);
        setCategory(item, ch);

        if (user.hasRole(Roles.NEWS_ADMIN)) {
            setOwner(item, ch);
            setForbidDiscussions(item, ch);

            Date date = ch.getDate(PARAM_PUBLISH_DATE, true);
            if (date != null)
                item.setCreated(date);
        }

        if (ch.isFailed() || params.get(PARAM_PREVIEW) != null) {
            relation.setChild(item);
            env.put(VAR_RELATION, relation);
            env.put(VAR_CATEGORIES, NewsCategories.getAllCategories());
            return FMTemplateSelector.select("EditNews", "edit", env, request);
        }

        persistence.update(item);
        AdminLogger.logEvent(user, "  edit | news "+relation.getId());

        FeedGenerator.updateNews();
        VariableFetcher.getInstance().refreshNews();

        UrlUtils urlUtils = (UrlUtils) env.get(Constants.VAR_URL_UTILS);
        urlUtils.redirect(response, urlUtils.getRelationUrl(relation));
        return null;
    }

    @ActionCheck(permittedRoles = {Roles.NEWS_ADMIN}, checkTicket = true, itemType = Item.NEWS)
    public String actionApprove() throws Exception {
        Persistence persistence = PersistenceFactory.getPersistence();
        UrlUtils urlUtils = (UrlUtils) env.get(Constants.VAR_URL_UTILS);

        if (relation.getParent().getId() != Constants.CAT_NEWS_POOL) {
            ServletUtils.addError(Constants.ERROR_GENERIC, "Zprávička již byla schválena!", env, request.getSession());
            urlUtils.redirect(response, urlUtils.getRelationUrl(relation));
            return null;
        }

        Item item = (Item) relation.getChild();
        Element element = (Element) item.getData().selectSingleNode("/data/approved_by");
        if (element != null) {
            ServletUtils.addError(Constants.ERROR_GENERIC, "Zprávička již byla schválena a čeká na čas publikování.", env, request.getSession());
            urlUtils.redirect(response, urlUtils.getRelationUrl(relation));
            return null;
        }

        boolean pooled = item.getCreated().getTime() >= System.currentTimeMillis();
        if (! pooled)
            item.setCreated(new Date());
        element = DocumentHelper.makeElement(item.getData(), "/data/approved_by");
        element.setText(Integer.toString(user.getId()));
        persistence.update(item);

        String title = item.getTitle();
        String url = UrlUtils.PREFIX_NEWS + "/" + URLManager.enforceRelativeURL(title);
        url = URLManager.protectFromDuplicates(url);
        relation.setUrl(url);
        persistence.update(relation);
        TagTool.assignDetectedTags(item, user);

        Map<String,List<Relation>> children = Tools.groupByType(item.getChildren());

        if (children.containsKey(Constants.TYPE_DISCUSSION)) {
            Relation disc = children.get(Constants.TYPE_DISCUSSION).get(0);

            String urldisc = url + "/diskuse";
            urldisc = URLManager.protectFromDuplicates(urldisc);
            disc.setUrl(urldisc);
            persistence.update(disc);
        } else
            EditDiscussion.createEmptyDiscussion(relation, user, persistence);

        AdminLogger.logEvent(user, "  approve | news " + relation.getUrl());

        if (pooled) {
            ServletUtils.addMessage("Zprávička čeká na čas publikování.", env, request.getSession());
        } else {
            relation.getParent().removeChildRelation(relation);
            relation.getParent().setId(Constants.CAT_NEWS);
            relation.setUpper(Constants.REL_NEWS);
            persistence.update(relation);
            relation.getParent().addChildRelation(relation);

            FeedGenerator.updateNews();
            VariableFetcher.getInstance().refreshNews();
        }

        urlUtils.redirect(response, url);
        return null;
    }

    @ActionCheck(permittedRoles = {Roles.NEWS_ADMIN}, itemType = Item.NEWS)
    public String actionRemove() {
        return FMTemplateSelector.select("EditNews", "remove", env, request);
    }

    @ActionCheck(permittedRoles = {Roles.NEWS_ADMIN}, itemType = Item.NEWS, checkPost = true, checkReferer = true)
    public String actionRemove2() throws Exception {
        Persistence persistence = PersistenceFactory.getPersistence();
        Item item = (Item) relation.getChild();
        User author = (User) persistence.findById(new User(item.getOwner()));

        Map<String,Object> map = new HashMap<String,Object>();
        map.put(VAR_RELATION, relation);
        map.put(VAR_AUTHOR, author);
        map.put(VAR_ADMIN, user);
        map.put(EmailSender.KEY_FROM, user.getEmail());

        if (author.getEmail() != null) {
            map.put(EmailSender.KEY_CC, "admini@abclinuxu.cz"); // inform group of admins too
            map.put(EmailSender.KEY_TO, author.getEmail());
        } else
            map.put(EmailSender.KEY_TO, "admini@abclinuxu.cz");

        map.put(EmailSender.KEY_RECEPIENT_UID, Integer.toString(author.getId()));
        map.put(EmailSender.KEY_SUBJECT, "zpravicka byla smazana");
        map.put(EmailSender.KEY_TEMPLATE, "/mail/rm_zpravicka.ftl");

        String text = (String) params.get(PARAM_MESSAGE);
        if ( text!=null && text.trim().length()>0 )
            map.put(VAR_MESSAGE, text);

        EmailSender.sendEmail(map);

        persistence.remove(relation);
        relation.getParent().removeChildRelation(relation);
        AdminLogger.logEvent(user, "  remove | news " + relation.getId());

        FeedGenerator.updateNews();
        VariableFetcher.getInstance().refreshNews();

        response.sendRedirect(response.encodeRedirectURL(UrlUtils.PREFIX_NEWS+"/dir/"+ Constants.REL_NEWS_POOL));
        return null;
    }

    @ActionCheck(permittedRoles = {Roles.NEWS_ADMIN}, itemType = Item.NEWS, checkTicket = true)
    public String actionLock() throws Exception {
        Persistence persistence = PersistenceFactory.getPersistence();
        Item item = (Item) relation.getChild();

        Element element = DocumentHelper.makeElement(item.getData(), "/data/locked_by");
        element.setText(Integer.toString(user.getId()));

        persistence.update(item);
        AdminLogger.logEvent(user, "  lock | news "+relation.getId());

        UrlUtils urlUtils = (UrlUtils) env.get(Constants.VAR_URL_UTILS);
        urlUtils.redirect(response, urlUtils.getRelationUrl(relation));
        return null;
    }

    @ActionCheck(permittedRoles = {Roles.NEWS_ADMIN}, itemType = Item.NEWS, checkTicket = true)
    public String actionUnlock() throws Exception {
        Persistence persistence = PersistenceFactory.getPersistence();
        Item item = (Item) relation.getChild();

        Element element = (Element) item.getData().selectSingleNode("/data/locked_by");
        element.detach();
        persistence.update(item);

        AdminLogger.logEvent(user, "  unlock | news "+relation.getId());

        UrlUtils urlUtils = (UrlUtils) env.get(Constants.VAR_URL_UTILS);
        urlUtils.redirect(response, urlUtils.getRelationUrl(relation));
        return null;
    }

    // setters

    /**
     * Updates news' content from parameters. Changes are not synchronized with persistence.
     * @param item news to be updated
     * @param text News text
     */
    private void setContent(Item item, ParameterChecker ch) {
        Document doc = item.getData();
        Element element = DocumentHelper.makeElement(doc, "/data/content");
        String text = ch.getHtml(PARAM_CONTENT, false, Rules.NEWS);
        
        element.setText(text);

        String perex = Tools.limitNewsLength(text);
        if (perex == null) {
            element = doc.getRootElement().element("perex");
            if (element != null)
                element.detach();
        } else {
            DocumentHelper.makeElement(doc, "/data/perex").setText(perex);
        }
    }

    /**
     * Updates news' content from parameters. Changes are not synchronized with persistence.
     * @param item news to be updated
     * @param ch A ParameterChecker instance
     */
    private void setCategory(Item item, ParameterChecker ch) {
        String text = ch.getString(PARAM_CATEGORY, true);
        if (!Misc.empty(text)) {
            List categories = NewsCategories.listKeys();
            if ( categories.contains(text) ) {
                item.setSubType(text);
            } else {
                log.warn("Nalezena neznama kategorie zpravicek '"+text+"'!");
            }
        }
    }

    /**
    * Sets the news item's owner from parameters. Changes are not synchronized with persistence.
    *
    * @param params map holding request's parameters
    * @param item   news to be updated
    * @return false, if there is a major error.
    */
    private void setOwner(Item item, ParameterChecker ch) {
        int suid = ch.getInteger(PARAM_UID, true);

        if (suid != 0) {
            try {
                User owner = Tools.createUser(suid);
                item.setOwner(owner.getId());
            } catch (Exception e) {
                ch.addError(PARAM_UID, "Neplatné UID uživatele!");
            }
        }
    }


    private void setForbidDiscussions(Item item, ParameterChecker ch) {
        String content = ch.getString(PARAM_FORBID_DISCUSSIONS, true);
        Element element = (Element) item.getData().selectSingleNode("/data/forbid_discussions");
        if ( element!=null )
            element.detach();

        if ( !content.isEmpty() ) {
            element = DocumentHelper.makeElement(item.getData(), "/data/forbid_discussions");
            element.setText(content);
        }
    }
}
