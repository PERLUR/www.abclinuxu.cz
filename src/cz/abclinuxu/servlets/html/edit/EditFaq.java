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

import cz.abclinuxu.data.Category;
import cz.abclinuxu.data.Item;
import cz.abclinuxu.data.Relation;
import cz.abclinuxu.data.User;
import cz.abclinuxu.exceptions.InvalidInputException;
import cz.abclinuxu.exceptions.MissingArgumentException;
import cz.abclinuxu.persistence.Persistence;
import cz.abclinuxu.persistence.PersistenceFactory;
import cz.abclinuxu.persistence.versioning.Versioning;
import cz.abclinuxu.persistence.versioning.VersioningFactory;
import cz.abclinuxu.servlets.AbcAction;
import cz.abclinuxu.servlets.Constants;
import cz.abclinuxu.servlets.utils.ServletUtils;
import cz.abclinuxu.servlets.utils.template.FMTemplateSelector;
import cz.abclinuxu.servlets.utils.url.URLManager;
import cz.abclinuxu.servlets.utils.url.UrlUtils;
import cz.abclinuxu.utils.InstanceUtils;
import cz.abclinuxu.utils.Misc;
import cz.abclinuxu.utils.TagTool;
import cz.abclinuxu.utils.feeds.FeedGenerator;
import cz.abclinuxu.utils.email.monitor.*;
import cz.abclinuxu.utils.freemarker.Tools;
import cz.abclinuxu.utils.parser.clean.HtmlPurifier;
import cz.abclinuxu.utils.parser.clean.HtmlChecker;
import cz.abclinuxu.utils.parser.clean.Rules;
import cz.abclinuxu.scheduler.VariableFetcher;
import cz.abclinuxu.security.ActionProtector;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;


/**
 * User: literakl
 * Date: 16.7.2005
 */
public class EditFaq implements AbcAction {
    static org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(EditFaq.class);

    public static final String PARAM_RELATION_SHORT = "rid";
    public static final String PARAM_TITLE = "title";
    public static final String PARAM_TEXT = "text";
    public static final String PARAM_PREVIEW = "preview";

    public static final String VAR_RELATION = "RELATION";
    public static final String VAR_PREVIEW = "PREVIEW";

    public static final String ACTION_ADD = "add";
    public static final String ACTION_ADD_STEP2 = "add2";
    public static final String ACTION_EDIT = "edit";
    public static final String ACTION_EDIT_STEP2 = "edit2";

    public String process(HttpServletRequest request, HttpServletResponse response, Map env) throws Exception {
        Map params = (Map) env.get(Constants.VAR_PARAMS);
        Relation relation = (Relation) InstanceUtils.instantiateParam(PARAM_RELATION_SHORT, Relation.class, params, request);
        String action = (String) params.get(PARAM_ACTION);

        if (ServletUtils.handleMaintainance(request, env)) {
            response.sendRedirect(response.encodeRedirectURL("/"));
            return null;
        }

        if (relation != null) {
            Tools.sync(relation);
            env.put(VAR_RELATION, relation);
        } else // if (!ACTION_ADD.equals(action)) // it's always in a subsection
            throw new MissingArgumentException("Chybí parametr rid!");


        // check permissions
        User user = (User) env.get(Constants.VAR_USER);
        if (user == null)
            return FMTemplateSelector.select("ViewUser", "login", env, request);

        if (ACTION_ADD.equals(action)) {
			if (!Tools.permissionsFor(user, relation).canCreate())
				return FMTemplateSelector.select("ViewUser", "forbidden", env, request);

            return actionAddStep1(request, env);
		}

        if (ACTION_ADD_STEP2.equals(action)) {
			if (!Tools.permissionsFor(user, relation).canCreate())
				return FMTemplateSelector.select("ViewUser", "forbidden", env, request);

            ActionProtector.ensureContract(request, EditFaq.class, true, true, true, false);
            return actionAddStep2(request, response, env, true);
        }

		if (!Tools.permissionsFor(user, relation).canModify())
				return FMTemplateSelector.select("ViewUser", "forbidden", env, request);

        if (ACTION_EDIT.equals(action))
            return actionEditStep1(request, env);

        if (ACTION_EDIT_STEP2.equals(action)) {
            ActionProtector.ensureContract(request, EditFaq.class, true, true, true, false);
            return actionEditStep2(request, response, env);
        }

        throw new MissingArgumentException("Nepodporovaná hodnota parametru action!");
    }

    /**
     * Displays form to add question.
     */
    private String actionAddStep1(HttpServletRequest request, Map env) throws Exception {
        return FMTemplateSelector.select("EditFaq", "add", env, request);
    }

    /**
     * Adds new FAQ entry to selected FAQ section.
     */
    public String actionAddStep2(HttpServletRequest request, HttpServletResponse response, Map env, boolean redirect) throws Exception {
        Persistence persistence = PersistenceFactory.getPersistence();
        Map params = (Map) env.get(Constants.VAR_PARAMS);
        User user = (User) env.get(Constants.VAR_USER);

        Relation parentRelation = (Relation) env.get(VAR_RELATION);
        Category section = (Category) persistence.findById(parentRelation.getChild());
        if (section.getType()!=Category.FAQ) {
            log.warn("Sekce " + section.getId() + " musí být typu FAQ - " + Category.FAQ);
            throw new InvalidInputException("Interní chyba - tato sekce není typu FAQ.");
        }

        Item item = new Item(0, Item.FAQ);
        item.setOwner(user.getId());
		item.setGroup(section.getGroup());
        item.setPermissions(section.getPermissions());

        Document document = DocumentHelper.createDocument();
        Element root = document.addElement("data");
        item.setData(document);
        Relation relation = new Relation(parentRelation.getChild(), item, parentRelation.getId());

        boolean canContinue = true;
        canContinue &= setQuestion(params, item, env);
        canContinue &= setText(params, root, env);

        if (!canContinue || params.get(PARAM_PREVIEW) != null) {
            if (!canContinue)
                params.remove(PARAM_PREVIEW);
            item.setInitialized(true);
            env.put(VAR_PREVIEW, item);
            return FMTemplateSelector.select("EditFaq", "add", env, request);
        }

        Versioning versioning = VersioningFactory.getVersioning();
        versioning.prepareObjectBeforeCommit(item, user.getId());
        persistence.create(item);
        versioning.commit(item, user.getId(), "Počáteční revize dokumentu");

        String title = item.getTitle();
        String url = parentRelation.getUrl() + "/" + URLManager.enforceRelativeURL(title);
        url = URLManager.protectFromDuplicates(url);
        relation.setUrl(url);
        persistence.create(relation);
        relation.getParent().addChildRelation(relation);
        TagTool.assignDetectedTags(item, user);

        // run monitor
        String absoluteUrl = "http://www.abclinuxu.cz" + relation.getUrl();
        MonitorAction action = new MonitorAction(user, UserAction.ADD, ObjectType.FAQ, relation, absoluteUrl);
        MonitorPool.scheduleMonitorAction(action);

        // refresh RSS
        FeedGenerator.updateFAQ();
        VariableFetcher.getInstance().refreshFaq();

        if (redirect) {
            UrlUtils urlUtils = (UrlUtils) env.get(Constants.VAR_URL_UTILS);
            urlUtils.redirect(response, url);
        } else
            env.put(VAR_RELATION, relation);

        return null;
    }

    /**
     * Displays form to edit question.
     */
    private String actionEditStep1(HttpServletRequest request, Map env) throws Exception {
        Map params = (Map) env.get(Constants.VAR_PARAMS);
        Relation relation = (Relation) env.get(VAR_RELATION);
        Item item = (Item) relation.getChild();

        Element root = item.getData().getRootElement();
        params.put(PARAM_TITLE, item.getTitle());
        Element element = (Element) root.element("text");
        params.put(PARAM_TEXT, element.getText());
        return FMTemplateSelector.select("EditFaq", "edit", env, request);
    }

    /**
     * Modifies existing FAQ entry to selected FAQ section.
     */
    protected String actionEditStep2(HttpServletRequest request, HttpServletResponse response, Map env) throws Exception {
        Persistence persistence = PersistenceFactory.getPersistence();
        Map params = (Map) env.get(Constants.VAR_PARAMS);
        User user = (User) env.get(Constants.VAR_USER);

        Relation relation = (Relation) env.get(VAR_RELATION);
        Item item = (Item) relation.getChild().clone();
        Item origItem = (Item) item.clone();
        item.setOwner(user.getId());
        Element root = item.getData().getRootElement();

        boolean canContinue = true;
        canContinue &= setQuestion(params, item, env);
        canContinue &= setText(params, root, env);
        canContinue &= ServletUtils.checkNoChange(item, origItem, env);
        String changesDescription = Misc.getRevisionString(params, env);
        canContinue &= ! Constants.ERROR.equals(changesDescription);

        if (!canContinue || params.get(PARAM_PREVIEW) != null) {
            if (!canContinue)
                params.remove(PARAM_PREVIEW);
            else
                env.put(VAR_PREVIEW, item);
            return FMTemplateSelector.select("EditFaq", "edit", env, request);
        }

        Versioning versioning = VersioningFactory.getVersioning();
        versioning.prepareObjectBeforeCommit(item, user.getId());
        persistence.update(item);
        versioning.commit(item, user.getId(), changesDescription);

        // run monitor
        String absoluteUrl = "http://www.abclinuxu.cz" + relation.getUrl();
        MonitorAction action = new MonitorAction(user, UserAction.EDIT, ObjectType.FAQ, relation, absoluteUrl);
        MonitorPool.scheduleMonitorAction(action);

        // refresh RSS
        FeedGenerator.updateFAQ();
        VariableFetcher.getInstance().refreshFaq();

        UrlUtils urlUtils = (UrlUtils) env.get(Constants.VAR_URL_UTILS);
        urlUtils.redirect(response, relation.getUrl());
        return null;
    }


    // setters

    /**
     * Updates question from parameters. Changes are not synchronized with persistence.
     *
     * @param params map holding request's parameters
     * @param env    environment
     * @return false, if there is a major error.
     */
    static boolean setQuestion(Map params, Item faq, Map env) {
        String tmp = (String) params.get(PARAM_TITLE);
        tmp = Misc.filterDangerousCharacters(tmp);
        if (tmp != null && tmp.length() > 0) {
            if (tmp.indexOf("<") != -1) {
                params.put(PARAM_TITLE, "");
                ServletUtils.addError(PARAM_TITLE, "Použití HTML značek je zakázáno!", env, null);
                return false;
            }
            if (tmp.indexOf('\n') != -1)
                tmp = tmp.replace('\n', ' ');

            faq.setTitle(tmp);
            faq.setSubType(Tools.limit(tmp, 30, ""));
        } else {
            ServletUtils.addError(PARAM_TITLE, "Zadejte titulek dotazu!", env, null);
            return false;
        }
        return true;
    }

    /**
     * Updates text of faq from parameters. Changes are not synchronized with persistence.
     *
     * @param params map holding request's parameters
     * @param root   root element of discussion to be updated
     * @param env    environment
     * @return false, if there is a major error.
     */
    static boolean setText(Map params, Element root, Map env) {
        String tmp = (String) params.get(PARAM_TEXT);
        tmp = Misc.filterDangerousCharacters(tmp);
        if (tmp != null && tmp.length() > 0) {
            try {
                tmp = HtmlPurifier.clean(tmp);
                HtmlChecker.check(Rules.DEFAULT, tmp);
            } catch (Exception e) {
                ServletUtils.addError(PARAM_TEXT, e.getMessage(), env, null);
                return false;
            }
            Element element = DocumentHelper.makeElement(root, "text");
            tmp = Tools.processLocalLinks(tmp, null);
            element.setText(tmp);
        } else {
            ServletUtils.addError(PARAM_TEXT, "Zadejte text odpovědi!", env, null);
            return false;
        }
        return true;
    }
}
